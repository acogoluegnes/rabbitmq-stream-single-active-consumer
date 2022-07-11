## RabbitMQ Streams 3.11 Sample Projects 

Sample projects to demonstrate RabbitMQ Streams features in RabbitMQ 3.11.

### Single Active Consumer

See [blog post](https://blog.rabbitmq.com/posts/2022/07/rabbitmq-3-11-feature-preview-single-active-consumer-for-streams/).

### Super Streams

Related blog post: TODO

#### Start the broker

Remove the Docker image if it's already there locally, to make sure to pull the latest image later:

```shell
docker rmi pivotalrabbitmq/rabbitmq-stream
```

Start the Broker:

```shell
docker run -it --rm --name rabbitmq -p 5552:5552 -p 5672:5672 -p 15672:15672 -e RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS='-rabbitmq_stream advertised_host localhost' pivotalrabbitmq/rabbitmq-stream
```

#### Get the Code

NB: requires JDK11+

Get the project:

```shell
cd /tmp
git clone https://github.com/acogoluegnes/rabbitmq-stream-single-active-consumer.git
cd rabbitmq-stream-single-active-consumer
```

#### Create the `invoices` Super Stream

Create an `invoices` super stream with 3 partitions:

```
docker exec rabbitmq rabbitmq-streams add_super_stream invoices --partitions 3
```

This should create an `invoices` direct exchange with 3 streams bound to it.
Check this on the exchange detail page: http://localhost:15672/#/exchanges/%2F/invoices

![image](https://user-images.githubusercontent.com/514737/152557053-10435ea9-67bb-480e-80a7-dda54da0fced.png)

#### Start a First Instance of the Consumer Application

Start the `instance-1` of the consumer application:

```
./mvnw -q compile exec:java -Dexec.mainClass=com.rabbitmq.stream.SuperStreamConsumer -Dexec.arguments="instance-1"
```

The application starts a "composite consumer" for the super stream, with the "single active consumer" flag enabled.
These are client-side features.
The client library registers a consumer to each partition (stream) of the super stream.
Consider this program as an instance/VM/container/pod of a user application.

NB: more information on the single active consumer and super stream support in the [stream Java client documentation](https://rabbitmq.github.io/rabbitmq-stream-java-client/sac/htmlsingle/#super-stream-sac)

Start the publishing application:

```
./mvnw -q compile exec:java -Dexec.mainClass=com.rabbitmq.stream.SuperStreamProducer
```

The publishing application uses a "composite producer", which is also a client-side feature. The client library creates a producer for each partition and each message is routed based on a client-side routing strategy (in this case, hashing the ID of the message, which is an incrementing sequence). The application publishes a message every second, which means a message ends in one of the partition every 3 seconds (if the hashing is well-balanced, which it should be).

The consumer application should report messages:

```
Starting consumer instance-1
Consumer instance-1 received message 0 from stream invoices-0.
Consumer instance-1 received message 1 from stream invoices-0.
Consumer instance-1 received message 2 from stream invoices-1.
Consumer instance-1 received message 3 from stream invoices-1.
Consumer instance-1 received message 4 from stream invoices-2.
Consumer instance-1 received message 5 from stream invoices-0.
Consumer instance-1 received message 6 from stream invoices-0.
Consumer instance-1 received message 7 from stream invoices-2.
Consumer instance-1 received message 8 from stream invoices-2.
Consumer instance-1 received message 9 from stream invoices-2.
Consumer instance-1 received message 10 from stream invoices-0.
...
```

The application reports for each message its ID and the partition it comes from.
The messages should be well-balanced between partitions (but it's not round-robin!).

#### Use the CLI to Get Some Insight

List all the stream consumers with `rabbitmqctl list_stream_consumers`:

```
docker exec rabbitmq rabbitmqctl list_stream_consumers \
  connection_pid,subscription_id,stream,messages_consumed,offset,offset_lag,active,activity_status
```

```
Listing stream consumers ...
┌────────────────┬─────────────────┬────────────┬───────────────────┬────────┬────────────┬────────┬─────────────────┐
│ connection_pid │ subscription_id │ stream     │ messages_consumed │ offset │ offset_lag │ active │ activity_status │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.1070.0> │ 0               │ invoices-0 │ 87                │ 140    │ 1          │ true   │ single_active   │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.1076.0> │ 0               │ invoices-2 │ 78                │ 131    │ 1          │ true   │ single_active   │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.1082.0> │ 0               │ invoices-1 │ 82                │ 135    │ 1          │ true   │ single_active   │
└────────────────┴─────────────────┴────────────┴───────────────────┴────────┴────────────┴────────┴─────────────────┘
```

You should see the 3 consumers of the "composite consumer", one for each partition, all active (each is the only one on a given partition).

List the consumer groups with `rabbitmqctl list_stream_consumer_groups`:

```
docker exec rabbitmq rabbitmqctl list_stream_consumer_groups
```

```
Listing stream consumer groups ...
┌────────────┬───────────┬─────────────────┬───────────┐
│ stream     │ reference │ partition_index │ consumers │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-0 │ my-app    │ 0               │ 1         │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-1 │ my-app    │ 1               │ 1         │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-2 │ my-app    │ 2               │ 1         │
└────────────┴───────────┴─────────────────┴───────────┘
```

A consumer group for each partition should show up. They should each report only one consumer (there's only 1 instance of the application so far).

Let's use `rabbitmqctl list_stream_group_consumers` for the group of the first partition:

```
docker exec rabbitmq rabbitmqctl list_stream_group_consumers --stream invoices-0 --reference my-app
```

```
Listing group consumers ...
┌─────────────────┬─────────────────────────────────────┬────────┐
│ subscription_id │ connection_name                     │ state  │
├─────────────────┼─────────────────────────────────────┼────────┤
│ 0               │ 172.17.0.1:36214 -> 172.17.0.2:5552 │ active │
└─────────────────┴─────────────────────────────────────┴────────┘
```

The group has only one consumer.

#### Start a Second Consumer Instance

Start a second instance of the consuming application:

```
./mvnw -q compile exec:java -Dexec.mainClass=com.rabbitmq.stream.SuperStreamConsumer -Dexec.arguments="instance-2"
```

This second instance will also create a composite consumer and the broker will start dispatching messages from the `invoices-1` partition stream to it:

```
Starting consumer instance-2
Consumer instance-2 received message 642 from stream invoices-1.
Consumer instance-2 received message 645 from stream invoices-1.
Consumer instance-2 received message 649 from stream invoices-1.
Consumer instance-2 received message 650 from stream invoices-1.
...
```

So `instance-2` gets messages from `invoices-1`.
The first instance should then stop receiving messages from this partition:

```
...
Consumer instance-1 received message 635 from stream invoices-0.
Consumer instance-1 received message 636 from stream invoices-0.
Consumer instance-1 received message 637 from stream invoices-2.
Consumer instance-1 received message 638 from stream invoices-1.     <--- "last" messages from invoices-1
Consumer instance-1 received message 639 from stream invoices-0.
Consumer instance-1 received message 640 from stream invoices-2.
Consumer instance-1 received message 641 from stream invoices-0.
Consumer instance-1 received message 643 from stream invoices-0.
Consumer instance-1 received message 644 from stream invoices-0.
Consumer instance-1 received message 646 from stream invoices-0.
Consumer instance-1 received message 647 from stream invoices-2.
Consumer instance-1 received message 648 from stream invoices-0.
Consumer instance-1 received message 656 from stream invoices-0.
```

The broker _rebalanced_ the dispatching of messages: the `invoices-1` partition went from the first instance to the second instance.

List all the stream consumers:

```
docker exec rabbitmq rabbitmqctl list_stream_consumers \
  connection_pid,subscription_id,stream,messages_consumed,offset,offset_lag,active,activity_status
```

```
Listing stream consumers ...
┌────────────────┬─────────────────┬────────────┬───────────────────┬────────┬────────────┬────────┬─────────────────┐
│ connection_pid │ subscription_id │ stream     │ messages_consumed │ offset │ offset_lag │ active │ activity_status │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.2204.0> │ 0               │ invoices-1 │ 487               │ 1210   │ 1          │ true   │ single_active   │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.1076.0> │ 0               │ invoices-2 │ 763               │ 1283   │ 0          │ true   │ single_active   │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.2192.0> │ 0               │ invoices-0 │ 0                 │ 0      │ 0          │ false  │ waiting         │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.1070.0> │ 0               │ invoices-0 │ 741               │ 1244   │ 1          │ true   │ single_active   │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.1082.0> │ 0               │ invoices-1 │ 229               │ 379    │ 0          │ false  │ waiting         │
├────────────────┼─────────────────┼────────────┼───────────────────┼────────┼────────────┼────────┼─────────────────┤
│ <11402.2198.0> │ 0               │ invoices-2 │ 0                 │ 0      │ 0          │ false  │ waiting         │
└────────────────┴─────────────────┴────────────┴───────────────────┴────────┴────────────┴────────┴─────────────────┘
```

The list should confirm that one former active consumer (in our case the one from connection `<11402.1082.0>`) became inactive in favor of a new one (the one from connection `<11402.2204.0>` here).

Listing the group of consumers should confirm there are 2 consumers on each partition now:

```
docker exec rabbitmq rabbitmqctl list_stream_consumer_groups
```

```
Listing stream consumer groups ...
┌────────────┬───────────┬─────────────────┬───────────┐
│ stream     │ reference │ partition_index │ consumers │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-0 │ my-app    │ 0               │ 2         │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-1 │ my-app    │ 1               │ 2         │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-2 │ my-app    │ 2               │ 2         │
└────────────┴───────────┴─────────────────┴───────────┘
```

#### Start a Third Consumer Instance

Start a third instance of the consuming application:

```
./mvnw -q compile exec:java -Dexec.mainClass=com.rabbitmq.stream.SuperStreamConsumer -Dexec.arguments="instance-3"
```

This third instance should get messages from `invoices-2`:

```
Starting consumer instance-3
Consumer instance-3 received message 388 from stream invoices-2.
Consumer instance-3 received message 390 from stream invoices-2.
Consumer instance-3 received message 391 from stream invoices-2.
Consumer instance-3 received message 392 from stream invoices-2.
Consumer instance-3 received message 393 from stream invoices-2.
Consumer instance-3 received message 397 from stream invoices-2.
...
```

The first instance should get only messages from `invoices-0` now:

```
...
Consumer instance-1 received message 376 from stream invoices-2.
Consumer instance-1 received message 377 from stream invoices-0.
Consumer instance-1 received message 380 from stream invoices-2.     <--- "last" messages from invoices-2
Consumer instance-1 received message 384 from stream invoices-0.
Consumer instance-1 received message 385 from stream invoices-0.
Consumer instance-1 received message 394 from stream invoices-0.
Consumer instance-1 received message 395 from stream invoices-0.
Consumer instance-1 received message 398 from stream invoices-0.
Consumer instance-1 received message 399 from stream invoices-0.
Consumer instance-1 received message 402 from stream invoices-0.
...
```

We have now 3 instances of our application and they each receive messages from a given partition of the super stream.
We scaled out the processing of the messages of the super stream.

`list_stream_consumer_groups` should show that each partition has 3 consumers:

```
docker exec rabbitmq rabbitmqctl list_stream_consumer_groups
```

```
┌────────────┬───────────┬─────────────────┬───────────┐
│ stream     │ reference │ partition_index │ consumers │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-0 │ my-app    │ 0               │ 3         │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-1 │ my-app    │ 1               │ 3         │
├────────────┼───────────┼─────────────────┼───────────┤
│ invoices-2 │ my-app    │ 2               │ 3         │
└────────────┴───────────┴─────────────────┴───────────┘
```

List the consumers of the consumers on the first partition:

```
docker exec rabbitmq rabbitmqctl list_stream_group_consumers --stream invoices-0 --reference my-app
```

```
┌─────────────────┬─────────────────────────────────────┬──────────┐
│ subscription_id │ connection_name                     │ state    │
├─────────────────┼─────────────────────────────────────┼──────────┤
│ 0               │ 172.17.0.1:46228 -> 172.17.0.2:5552 │ active   │
├─────────────────┼─────────────────────────────────────┼──────────┤
│ 0               │ 172.17.0.1:46248 -> 172.17.0.2:5552 │ inactive │
├─────────────────┼─────────────────────────────────────┼──────────┤
│ 0               │ 172.17.0.1:46266 -> 172.17.0.2:5552 │ inactive │
└─────────────────┴─────────────────────────────────────┴──────────┘
```

There should be 1 active consumer (the one from the first instance, which is active since the beginning) and 2 inactive consumers (the one from instance 2 and 3, that we started after instance 1).

#### Stop the Consumer Instances

Stop `instance-1` (the first you started) with `Ctrl-C`. This should trigger a significant rebalancing:
* `instance-2`: formerly `invoices-1`, now `invoices-0` and `invoices-2`
* `instance-3`: formerly `invoices-2`, now `invoices-1`

Listing the consumers of the group for `invoices-0` should confirm `instance-2` took over (it was the second in line):

```
docker exec rabbitmq rabbitmqctl list_stream_group_consumers --stream invoices-0 --reference my-app
```

```
Listing group consumers ...
┌─────────────────┬─────────────────────────────────────┬──────────┐
│ subscription_id │ connection_name                     │ state    │
├─────────────────┼─────────────────────────────────────┼──────────┤
│ 0               │ 172.17.0.1:46248 -> 172.17.0.2:5552 │ active   │
├─────────────────┼─────────────────────────────────────┼──────────┤
│ 0               │ 172.17.0.1:46266 -> 172.17.0.2:5552 │ inactive │
└─────────────────┴─────────────────────────────────────┴──────────┘
```

Stop `instance-1` now. `instance-2` should take now the 3 partitions.

Stop `instance-3`.

`rabbitmqctl list_stream_consumer_groups` should return nothing:

```
docker exec rabbitmq rabbitmqctl list_stream_consumer_groups
```

```
Listing stream consumer groups ...
```

Trying to list the consumers of the group on `invoices-0` should return an error because the group no longer exists:

```
docker exec rabbitmq rabbitmqctl list_stream_group_consumers --stream invoices-0 --reference my-app
```

```
Listing group consumers ...
Error:
The group does not exist
```

Stop the publisher.

Stop the broker Docker container.
