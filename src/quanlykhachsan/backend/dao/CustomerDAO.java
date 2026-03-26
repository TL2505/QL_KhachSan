/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Customer;
import java.util.ArrayList;

/**
 *
 * @author Admin
 */
public interface CustomerDAO {

//    add Customer
    public void addCustomer(Customer customer);

//    update Customer
    public void updateCustomer(Customer customer);

//    delete Customer
    public void deleteCustomer(Customer customer);

//    list of Customer 
    public ArrayList<Customer> selectCustomer();

    public void comboBoxCustomer();

    public java.util.List<Customer> findAll();
    public Customer findById(int id);
    public boolean insert(Customer customer);
}
