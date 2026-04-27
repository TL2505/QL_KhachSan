package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.customer.CustomerDAO;
import quanlykhachsan.backend.customer.CustomerDAOImpl;
import quanlykhachsan.backend.customer.Customer;

import java.util.List;

public class CustomerService {

    private CustomerDAO customerDAO = new CustomerDAOImpl();

    public List<Customer> getAllCustomers() {
        return customerDAO.findAll();
    }

    public boolean addCustomer(Customer customer) throws Exception {
        return customerDAO.insert(customer);
    }

    public boolean updateCustomer(Customer customer) throws Exception {
        customerDAO.updateCustomer(customer);
        return true;
    }

    public Customer getCustomerById(int id) {
        return customerDAO.findById(id);
    }

    public boolean deleteCustomer(int id) throws Exception {
        Customer c = new Customer();
        c.setId(id);
        customerDAO.deleteCustomer(c);
        return true;
    }
}