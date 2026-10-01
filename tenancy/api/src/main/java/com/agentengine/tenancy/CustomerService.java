package com.agentengine.tenancy;

import com.agentengine.tenancy.beans.Customer;
import com.agentengine.util.ms.client.MicroService;

@MicroService("tenancy")
public interface CustomerService {

  String CUSTOMER_BY_DOMAIN_CACHE = "CUSTOMER_BY_DOMAIN";

  Customer getByDomain(String domain);

  Customer create(Customer customer);
}
