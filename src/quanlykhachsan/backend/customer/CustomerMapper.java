package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.customer.dto.CustomerResponse;

public class CustomerMapper {
    public static CustomerResponse toCustomerResponse(Customer customer) {
        if (customer == null) return null;
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setFullName(customer.getFullName());
        response.setIdentityCard(customer.getIdentityCard());
        response.setPhone(customer.getPhone());
        response.setEmail(customer.getEmail());
        response.setAddress(customer.getAddress());
        response.setVip(customer.isVip());
        response.setLoyaltyPoints(customer.getLoyaltyPoints());
        response.setTotalLoyaltyPoints(customer.getTotalLoyaltyPoints());
        response.setLoyaltyLevel(customer.getLoyaltyLevel());
        return response;
    }
}
