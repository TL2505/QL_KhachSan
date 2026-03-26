package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Payment;
import java.util.ArrayList;

public interface PaymentDAO {

//    add Payment
    public void addPayment(Payment payment);

//    update Payment
    public void updatePayment(Payment payment);

//    delete Payment
    public void deletePayment(Payment payment);

//    list of Payment
    public ArrayList<Payment> selectPayment();

    public void comboBoxPayment();

}
