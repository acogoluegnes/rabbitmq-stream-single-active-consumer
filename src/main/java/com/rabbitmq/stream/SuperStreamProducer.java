package com.rabbitmq.stream;

public class SuperStreamProducer {
  public static void main(String[] args) throws InterruptedException {
    try (Environment environment =
        Environment.builder()
            .maxConsumersByConnection(1)
            .maxTrackingConsumersByConnection(1)
            .build()) {

      String superStream = "invoices";

      System.out.println("Starting producer");

      Producer producer =
          environment.producerBuilder().stream(superStream)
              .routing(msg -> msg.getProperties().getMessageIdAsString())
              .producerBuilder()
              .build();

      long idSequence = 0;
      while (true) {
        producer.send(
            producer.messageBuilder().properties().messageId(idSequence++).messageBuilder().build(),
            confirmationStatus -> {});
        Thread.sleep(1000);
      }
    }
  }
}
