package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.CustomerDAO;
import quanlykhachsan.backend.daoimpl.CustomerDAOImpl;
import quanlykhachsan.backend.model.Customer;

import java.util.List;

public class CustomerService {

    private CustomerDAO customerDAO = new CustomerDAOImpl();

    public List<Customer> getAllCustomers() {
        return customerDAO.findAll();
    }

    public boolean addCustomer(Customer customer) {
        return customerDAO.insert(customer);
    }

    public boolean updateCustomer(Customer customer) {
        try {
            customerDAO.updateCustomer(customer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Customer getCustomerById(int id) {
        return customerDAO.findById(id);
    }

    public boolean deleteCustomer(int id) {
        try {
            Customer c = new Customer();
            c.setId(id);
            customerDAO.deleteCustomer(c);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}