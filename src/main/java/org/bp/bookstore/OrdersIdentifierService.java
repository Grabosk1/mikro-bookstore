package org.bp.bookstore;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrdersIdentifierService {
//	HashMap<String, OrderIds> ordersIds = new HashMap<>();
//
//	public String generateOrderId() {
//		String orderId = UUID.randomUUID().toString();
//		ordersIds.put(orderId, new OrderIds());
//		return orderId;
//	}
//
//	public void assignBooksOrderId(String id, String orderId) {
//		ordersIds.get(id).setBooksOrderId(orderId);
//	}
//
//	public String getBooksOrderId(String id) {
//		return ordersIds.get(id).getBooksOrderId();
//	}
//
//	public static class OrderIds {
//		private String booksOrderId;
//
//		public String getBooksOrderId() {
//			return booksOrderId;
//		}
//
//		public void setBooksOrderId(String BooksOrderId) {
//			this.booksOrderId = BooksOrderId;
//		}
//
//	}
//
	public String getOrderingIdentifier() {
		return UUID.randomUUID().toString();
	}
}