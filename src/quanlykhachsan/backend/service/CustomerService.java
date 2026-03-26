package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.CustomerDAO;
import quanlykhachsan.backend.model.Customer;

import java.util.List;

public class CustomerService {

    private CustomerDAO customerDAO = new CustomerDAO();

    public List<Customer> getAllCustomers() {
        return customerDAO.findAll();
    }

    public boolean addCustomer(Customer customer) {
        return customerDAO.insert(customer);
    }

    public Customer getCustomerById(int id) {
        return customerDAO.findById(id);
    }
}