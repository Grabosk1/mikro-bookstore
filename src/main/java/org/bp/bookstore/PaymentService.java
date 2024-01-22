package org.bp.bookstore;


import org.bp.bookstore.model.OrderBookRequest;
import org.bp.bookstore.model.OrderingInfo;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;

@Service
public class PaymentService {
	private HashMap<String, PaymentData> payments;
	
	@PostConstruct
	void init() {
		payments=new HashMap<>();
	}
	
	public static class PaymentData {
		OrderBookRequest orderBookRequest;
		OrderingInfo itemOrderingInfo;
		OrderingInfo deliveryOrderingInfo;
		public boolean isReady() {
			return orderBookRequest!=null && itemOrderingInfo!=null && deliveryOrderingInfo!=null;
		}
	}
	
	public synchronized boolean addOrderBookRequest(String orderBookId, OrderBookRequest orderBookRequest) {
		PaymentData paymentData = getPaymentData(orderBookId);
		paymentData.orderBookRequest=orderBookRequest;
		return paymentData.isReady();
	}
	

	public synchronized boolean addOrderingInfo(String orderBookId, OrderingInfo orderingInfo, String serviceType) {
		PaymentData paymentData = getPaymentData(orderBookId);
		if (serviceType.equals("delivery"))
			paymentData.deliveryOrderingInfo=orderingInfo;
		else 
			paymentData.itemOrderingInfo=orderingInfo;		
		return paymentData.isReady();
	}	
	
	
	public synchronized PaymentData getPaymentData(String orderBookId) {
		PaymentData paymentData = payments.get(orderBookId);
		if (paymentData==null) {
			paymentData = new PaymentData();
			payments.put(orderBookId, paymentData);
		}
		return paymentData;
	}

	


	

}
