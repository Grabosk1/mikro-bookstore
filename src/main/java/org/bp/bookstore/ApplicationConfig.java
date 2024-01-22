package org.bp.bookstore;



import org.bp.bookstore.state.ProcessingEvent;
import org.bp.bookstore.state.ProcessingState;
import org.bp.bookstore.state.StateMachineBuilder;
import org.bp.bookstore.state.StateService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;


@Configuration
public class ApplicationConfig {
    
    @Bean(name="basicStateMachineBuilder")
    public StateMachineBuilder basicStateMachineBuilder() {
    	StateMachineBuilder smb = new StateMachineBuilder();
    	smb.initialState(ProcessingState.NONE)
        .add(ProcessingState.NONE,ProcessingEvent.START,ProcessingState.STARTED)
        .add(ProcessingState.STARTED,ProcessingEvent.FINISH,ProcessingState.FINISHED)
    	.add(ProcessingState.NONE,ProcessingEvent.CANCEL,ProcessingState.CANCELLED)	            
    	.add(ProcessingState.STARTED,ProcessingEvent.CANCEL,ProcessingState.CANCELLED)	            
    	.add(ProcessingState.FINISHED,ProcessingEvent.CANCEL,ProcessingState.CANCELLED)	            
    	.add(ProcessingState.CANCELLED,ProcessingEvent.START,ProcessingState.CANCELLED)	            
    	.add(ProcessingState.CANCELLED,ProcessingEvent.FINISH,ProcessingState.CANCELLED)	            
    	.add(ProcessingState.FINISHED,ProcessingEvent.COMPLETE,ProcessingState.COMPLETED)
		.add(ProcessingState.CANCELLED,ProcessingEvent.COMPLETE,ProcessingState.COMPLETED)	  	            
         ;
    	return smb;
    }

    @Bean
    @Scope("prototype")
    public StateService stateService(@Qualifier("basicStateMachineBuilder") StateMachineBuilder stateMachineBuilder) {
    	return new StateService (stateMachineBuilder);
    }
}