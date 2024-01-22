package org.bp.bookstore;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.bp.bookstore.exceptions.DeliveryException;
import org.bp.bookstore.exceptions.ItemException;
import org.bp.bookstore.model.*;
import org.bp.bookstore.state.ProcessingEvent;
import org.bp.bookstore.state.ProcessingState;
import org.bp.bookstore.state.StateService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.apache.camel.model.rest.RestParamType.body;

@Component
public class CamelRouter extends RouteBuilder {

    @org.springframework.beans.factory.annotation.Autowired
    OrdersIdentifierService orderingIdentifierService;

    @org.springframework.beans.factory.annotation.Autowired
    PaymentService paymentService;

    @org.springframework.beans.factory.annotation.Autowired
    StateService deliveryStateService;

    @org.springframework.beans.factory.annotation.Autowired
    StateService itemStateService;

    @org.springframework.beans.factory.annotation.Value("${book.kafka.server}")
    private String bookKafkaServer;
    @org.springframework.beans.factory.annotation.Value("${book.service.type}")
    private String bookServiceType;

    final String booksOrderUrl = "http://localhost:8081/soap-api/service/bookstore/order";

    @Override
    public void configure() throws Exception {
        if (bookServiceType.equals("all") || bookServiceType.equals("item"))
            orderItemExceptionHandlers();
        if (bookServiceType.equals("all") || bookServiceType.equals("delivery"))
            orderDeliveryExceptionHandlers();
        if (bookServiceType.equals("all") || bookServiceType.equals("gateway"))
            gateway();
        if (bookServiceType.equals("all") || bookServiceType.equals("item"))
            item();
        if (bookServiceType.equals("all") || bookServiceType.equals("delivery"))
            delivery();
        if (bookServiceType.equals("all") || bookServiceType.equals("payment"))
            payment();
    }

    private void orderDeliveryExceptionHandlers() {
        onException(DeliveryException.class)
                .process((exchange) -> {
                            ExceptionResponse er = new ExceptionResponse();
                            er.setTimestamp(OffsetDateTime.now());
                            Exception cause =
                                    exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                            er.setMessage(cause.getMessage());
                            exchange.getMessage().setBody(er);
                        }
                )
                .marshal().json()
                .to("stream:out")
                .setHeader("serviceType", constant("delivery"))
                .to("kafka:BookOrderingFailTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType)
                .handled(true)
        ;
    }

    private void orderItemExceptionHandlers() {
        onException(ItemException.class)
                .process((exchange) -> {
                            ExceptionResponse er = new ExceptionResponse();
                            er.setTimestamp(OffsetDateTime.now());
                            Exception cause =
                                    exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                            er.setMessage(cause.getMessage());
                            exchange.getMessage().setBody(er);
                        }
                )
                .marshal().json()
                .to("stream:out")
                .setHeader("serviceType", constant("item"))
                .to("kafka:BookOrderingFailTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType)
                .handled(true)
        ;
    }


    private void gateway() {
        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("prettyPrint", "true")
                .enableCORS(true)
                .contextPath("/api")
// turn on swagger api-doc
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Micro Book ordering API")
                .apiProperty("api.version", "1.0.0");

        rest("/book").description("Micro Book ordering REST service")
                .consumes("application/json")
                .produces("application/json")
                .post("/ordering").description("Order a book").type(OrderBookRequest.class).outType(OrderingInfo.class)
                .param().name("body").type(body).description("The order of book").endParam()
                .responseMessage().code(200).message("Book successfully ordered").endResponseMessage()
                .to("direct:orderBook");

        from("direct:orderBook").routeId("orderBook")
                .log("orderBook fired")
                .process((exchange) -> {
                    exchange.getMessage().setHeader("orderingBookId",
                            orderingIdentifierService.getOrderingIdentifier());
                })
                .to("direct:BookOrderRequest")
                .to("direct:orderRequester");

        from("direct:orderRequester").routeId("orderRequester")
                .log("orderRequester fired")
                .process(
                        (exchange) -> {
                            exchange.getMessage().setBody(Utils.prepareOrderingInfo(
                                    exchange.getMessage().getHeader("orderingBookId", String.class), null));
                        }
                );

        from("direct:BookOrderRequest").routeId("BookOrderRequest")
                .log("brokerTopic fired")
                .marshal().json()
                .to("kafka:BookReqTopic?brokers="+ bookKafkaServer + "&groupId=" + bookServiceType);
    }

    private void item() {
        from("kafka:BookReqTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType).routeId("orderItem")
                .log("fired orderItem")
                .unmarshal().json(JsonLibrary.Jackson, OrderBookRequest.class)
                .process(
                        (exchange) -> {
                            String orderingBookId =
                                    exchange.getMessage().getHeader("orderingBookId", String.class);
                            ProcessingState previousState =
                                    itemStateService.sendEvent(orderingBookId, ProcessingEvent.START);
                            if (previousState != ProcessingState.CANCELLED) {
                                OrderingInfo oi = new OrderingInfo();
                                oi.setId(orderingIdentifierService.getOrderingIdentifier());
                                OrderBookRequest otr = exchange.getMessage().getBody(OrderBookRequest.class);
                                if (otr != null && otr.getItem() != null
                                        && otr.getItem().getTitle() != null) {
                                    String title = otr.getItem().getTitle();
                                    BigDecimal price = otr.getItem().getPrice();
                                    if (title.equals("Popioły")) {
                                        oi.setCost(price.add(new BigDecimal(1000)) );
                                    } else if (title.equals("Zły")) {
                                        throw new ItemException("Book not in market: " + title);
                                    } else {
                                        oi.setCost(price);
                                    }
                                }
                                exchange.getMessage().setBody(oi);
                                previousState = itemStateService.sendEvent(orderingBookId,
                                        ProcessingEvent.FINISH);
                            }
                            exchange.getMessage().setHeader("previousState", previousState);
                        }
                )
                .marshal().json()
                .to("stream:out")
                .choice()
                .when(header("previousState").isEqualTo(ProcessingState.CANCELLED))
                .to("direct:orderItemCompensationAction")
                .otherwise()
                .setHeader("serviceType", constant("item"))
                .to("kafka:OrderingInfoTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType)
                .endChoice()
        ;

        from("kafka:BookOrderingFailTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType).routeId("orderItemCompensation")
                .log("fired orderItemCompensation")
                .unmarshal().json(JsonLibrary.Jackson, ExceptionResponse.class)
                .choice()
                .when(header("serviceType").isNotEqualTo("item"))
                .process((exchange) -> {
                    String orderingBookId = exchange.getMessage().getHeader("orderingBookId",
                            String.class);
                    ProcessingState previousState = itemStateService.sendEvent(orderingBookId,
                            ProcessingEvent.CANCEL);
                    exchange.getMessage().setHeader("previousState", previousState);
                })
                .choice()
                .when(header("previousState").isEqualTo(ProcessingState.FINISHED))
                .to("direct:orderItemCompensationAction")
                .endChoice()
                .endChoice();
        from("direct:orderItemCompensationAction").routeId("orderItemCompensationAction")
                .log("fired orderItemCompensationAction")
                .to("stream:out");
    }

    private void delivery() {
        from("kafka:BookReqTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType).routeId("orderDelivery")
                .log("fired orderDelivery")
                .unmarshal().json(JsonLibrary.Jackson, OrderBookRequest.class)
                .process(
                        (exchange) -> {
                            String orderingBookId =
                                    exchange.getMessage().getHeader("orderingBookId", String.class);
                            ProcessingState previousState =
                                    deliveryStateService.sendEvent(orderingBookId, ProcessingEvent.START);
                            if (previousState != ProcessingState.CANCELLED) {
                                OrderingInfo oi = new OrderingInfo();
                                oi.setId(orderingIdentifierService.getOrderingIdentifier());
                                OrderBookRequest obr =
                                        exchange.getMessage().getBody(OrderBookRequest.class);
                                if (obr != null && obr.getDelivery() != null
                                        && obr.getDelivery().getCompany() != null) {
                                    String company = obr.getDelivery().getCompany();
                                    if (company.equals("GLS")) {
                                        throw new DeliveryException("Not serviced delivery company: " + company);
                                    } else if (company.equals("DHL")) {
                                        oi.setCost(new BigDecimal(30));
                                    } else {
                                        oi.setCost(new BigDecimal(15));
                                    }
                                }
                                exchange.getMessage().setBody(oi);
                                previousState = deliveryStateService.sendEvent(orderingBookId,
                                        ProcessingEvent.FINISH);
                            }
                            exchange.getMessage().setHeader("previousState", previousState);
                        }
                )
                .marshal().json()
                .to("stream:out")
                .choice()
                .when(header("previousState").isEqualTo(ProcessingState.CANCELLED))
                .to("direct:orderDeliveryCompensationAction")
                .otherwise()
                .setHeader("serviceType", constant("delivery"))
                .to("kafka:OrderingInfoTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType)
                .endChoice()
        ;

        from("kafka:BookOrderingFailTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType).routeId("orderDeliveryCompensation")
                .log("fired orderDeliveryCompensation")
                .unmarshal().json(JsonLibrary.Jackson, ExceptionResponse.class)
                .choice()
                .when(header("serviceType").isNotEqualTo("delivery"))
                .process((exchange) -> {
                    String orderingBookId = exchange.getMessage().getHeader("orderingBookId", String.class);
                    ProcessingState previousState = deliveryStateService.sendEvent(orderingBookId,
                            ProcessingEvent.CANCEL);
                    exchange.getMessage().setHeader("previousState", previousState);
                })
                .choice()
                .when(header("previousState").isEqualTo(ProcessingState.FINISHED))
                .to("direct:orderDeliveryCompensationAction")
                .endChoice()
                .endChoice();

        from("direct:orderDeliveryCompensationAction").routeId("orderDeliveryCompensationAction")
                .log("fired orderDeliveryCompensationAction")
                .to("stream:out");
    }

    private void payment() {
        from("kafka:OrderingInfoTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType)
                .routeId("paymentOrderingInfo")
                .log("fired paymentOrderingInfo")
                .unmarshal().json(JsonLibrary.Jackson, OrderingInfo.class)
                .process(
                        (exchange) -> {
                            String orderingBookId =
                                    exchange.getMessage().getHeader("orderingBookId", String.class);
                            boolean isReady = paymentService.addOrderingInfo(
                                    orderingBookId,
                                    exchange.getMessage().getBody(OrderingInfo.class),
                                    exchange.getMessage().getHeader("serviceType", String.class));
                            exchange.getMessage().setHeader("isReady", isReady);
                        }
                )
                .choice()
                .when(header("isReady").isEqualTo(true)).to("direct:finalizePayment")
                .endChoice();

        from("kafka:BookReqTopic?brokers=" + bookKafkaServer + "&groupId=" + bookServiceType).routeId("paymentBookReq")
                .log("fired paymentBookReq")
                .unmarshal().json(JsonLibrary.Jackson, OrderBookRequest.class)
                .process(
                        (exchange) -> {
                            String orderingBookId = exchange.getMessage()
                                    .getHeader("orderingBookId", String.class);
                            boolean isReady = paymentService.addOrderBookRequest(
                                    orderingBookId,
                                    exchange.getMessage().getBody(OrderBookRequest.class));
                            exchange.getMessage().setHeader("isReady", isReady);
                        }
                )
                .choice()
                .when(header("isReady").isEqualTo(true)).to("direct:finalizePayment")
                .endChoice();

        from("direct:finalizePayment").routeId("finalizePayment")
                .log("fired finalizePayment")
                .process(
                        (exchange) -> {
                            String orderingBookId = exchange.getMessage().
                                    getHeader("orderingBookId", String.class);
                            PaymentService.PaymentData paymentData =
                                    paymentService.getPaymentData(orderingBookId);
                            BigDecimal itemCost = paymentData.itemOrderingInfo.getCost();
                            BigDecimal deliveryCost = paymentData.deliveryOrderingInfo.getCost();
                            BigDecimal totalCost = itemCost.add(deliveryCost);
                            OrderingInfo bookOrderingInfo = new OrderingInfo();
                            bookOrderingInfo.setId(orderingBookId);
                            bookOrderingInfo.setCost(totalCost);
                            exchange.getMessage().setBody(bookOrderingInfo);
                        }
                )
                .to("direct:notification");

        from("direct:notification").routeId("notification")
                .log("fired notification")
                .marshal().json()
                .to("http://localhost:8085/payment")
                .unmarshal().json(JsonLibrary.Jackson, PaymentResponse.class)
                .to("stream:out");
    }
}
