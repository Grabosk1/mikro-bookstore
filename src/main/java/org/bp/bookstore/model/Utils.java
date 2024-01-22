package org.bp.bookstore.model;

import java.math.BigDecimal;

public class Utils {
	static public OrderingInfo prepareOrderingInfo(String orderingId, BigDecimal cost) {
		OrderingInfo orderingInfo = new OrderingInfo();
		orderingInfo.setId(orderingId);
		orderingInfo.setCost(cost);
		return orderingInfo;
	}

}