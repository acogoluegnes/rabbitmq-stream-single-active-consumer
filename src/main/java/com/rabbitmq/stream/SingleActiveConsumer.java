package com.rabbitmq.stream;

import java.util.Scanner;
import java.util.stream.IntStream;

public class SingleActiveConsumer {

  public static void main(String[] args) {
    try (Environment environment =
        Environment.builder()
            .maxConsumersByConnection(1)
            .maxTrackingConsumersByConnection(1)
            .build()) {

      String stream = "single-active-consumer";
      String reference = "my-app";
      environment.streamCreator().stream(stream).create();
      System.out.println("Created stream " + stream);

      IntStream.range(0, 3)
          .forEach(
              i -> {
                System.out.println("Starting consumer " + i);
                environment.consumerBuilder().stream(stream)
                    .name(reference)
                    .singleActiveConsumer()
                    .autoTrackingStrategy()
                    .messageCountBeforeStorage(10)
                    .builder()
                    .messageHandler(
                        (context, message) -> {
                          System.out.println("Consumer " + i + " receive a message.");
                        })
                    .build();
              });
      Scanner keyboard = new Scanner(System.in);
      keyboard.nextLine();
    }

  }
}
