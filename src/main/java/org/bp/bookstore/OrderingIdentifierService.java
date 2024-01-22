package org.bp.bookstore;


import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderingIdentifierService {
	
	public String getOrderingIdentifier() {
		return UUID.randomUUID().toString();
	}
	

}
