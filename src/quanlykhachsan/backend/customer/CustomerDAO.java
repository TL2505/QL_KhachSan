/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.customer.Customer;
import java.util.ArrayList;

/**
 *
 * @author Admin
 */
public interface CustomerDAO {

//    add Customer
    public void addCustomer(Customer customer) throws Exception;

//    update Customer
    public void updateCustomer(Customer customer) throws Exception;

//    delete Customer
    public void deleteCustomer(Customer customer) throws Exception;

//    list of Customer 
    public ArrayList<Customer> selectCustomer();

    public void comboBoxCustomer();

    public java.util.List<Customer> findAll();
    public Customer findById(int id);
    public boolean insert(Customer customer) throws Exception;
    public int addAndReturnId(Customer customer) throws Exception;
    public void updateLoyaltyPoints(int customerId, int currentPointsChange, int totalPointsChange, String newLevel) throws Exception;
}
