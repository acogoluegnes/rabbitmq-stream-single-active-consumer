package com.rabbitmq.stream;

import java.util.Scanner;

public class SuperStreamConsumer {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("Specify the instance name!");
      System.exit(1);
    }
    String instanceName = args[0];
    try (Environment environment =
        Environment.builder()
            .maxConsumersByConnection(1)
            .maxTrackingConsumersByConnection(1)
            .build()) {

      String superStream = "invoices";
      String reference = "my-app";

      System.out.println("Starting consumer " + instanceName);
      environment
          .consumerBuilder()
          .superStream(superStream)
          .name(reference)
          .singleActiveConsumer()
          .autoTrackingStrategy()
          .messageCountBeforeStorage(10)
          .builder()
          .messageHandler(
              (context, message) -> {
                System.out.println(
                    "Consumer "
                        + instanceName
                        + " received message "
                        + message.getProperties().getMessageId()
                        + " from stream "
                        + context.stream()
                        + ".");
              })
          .build();
      Scanner keyboard = new Scanner(System.in);
      keyboard.nextLine();
    }
  }
}
