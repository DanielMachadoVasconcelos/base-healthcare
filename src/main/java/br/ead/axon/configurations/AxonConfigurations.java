package br.ead.axon.configurations;

import lombok.extern.log4j.Log4j2;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.config.EventProcessingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
public class AxonConfigurations {

    public void configure(EventProcessingConfigurer configurer) {
        configurer.usingSubscribingEventProcessors(); // make all processors subscribing
    }
}
