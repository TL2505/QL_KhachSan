package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.customer.CustomerService;
import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.customer.dto.CustomerCreateRequest;
import quanlykhachsan.backend.customer.dto.CustomerResponse;
import quanlykhachsan.backend.customer.CustomerMapper;

public class CustomerController implements HttpHandler {

    private CustomerService customerService = new CustomerService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        Gson gson = JsonUtil.getGson();

        try {
            // 1. GET /api/customers or /api/customers/{id}
            if ("GET".equalsIgnoreCase(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                
                if (parts.length >= 4) {
                    // GET /api/customers/{id}
                    int id = Integer.parseInt(parts[3]);
                    Customer c = customerService.getCustomerById(id);
                    if (c != null) {
                        CustomerResponse dto = CustomerMapper.toCustomerResponse(c);
                        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(dto));
                    } else {
                        ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy khách hàng!"));
                    }
                } else {
                    // GET /api/customers
                    List<Customer> customers = customerService.getAllCustomers();
                    List<CustomerResponse> dtoList = new java.util.ArrayList<>();
                    for (Customer c : customers) {
                        dtoList.add(CustomerMapper.toCustomerResponse(c));
                    }
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(dtoList));
                }
            } 
            // 2. POST /api/customers
            else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, gson);
            }
            // 3. PUT /api/customers/{id}
            else if ("PUT".equalsIgnoreCase(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length < 4) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu ID khách hàng!"));
                    return;
                }
                int id = Integer.parseInt(parts[3]);
                handlePut(exchange, id, gson);
            }
            // 4. DELETE /api/customers/{id}
            else if ("DELETE".equalsIgnoreCase(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length < 4) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu ID khách hàng!"));
                    return;
                }
                int id = Integer.parseInt(parts[3]);
                handleDelete(exchange, id);
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error(e.getMessage()));
        }
    }

    private void handlePost(HttpExchange exchange, Gson gson) throws Exception {
        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        CustomerCreateRequest req = gson.fromJson(requestBody, CustomerCreateRequest.class);
        
        String name = req.getName();
        String phone = req.getPhone();
        String cccd = req.getCccd();

        if (name == null || phone == null || cccd == null) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu thông tin!"));
            return;
        }

        Customer c = new Customer();
        c.setFullName(name);
        c.setPhone(phone);
        c.setIdentityCard(cccd);

        if (customerService.addCustomer(c)) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Thêm khách hàng thành công!"));
        } else {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Không thể thêm khách hàng!"));
        }
    }

    private void handlePut(HttpExchange exchange, int id, Gson gson) throws Exception {
        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        CustomerCreateRequest req = gson.fromJson(requestBody, CustomerCreateRequest.class);
        
        String name = req.getName();
        String phone = req.getPhone();
        String cccd = req.getCccd();

        Customer c = customerService.getCustomerById(id);
        if (c == null) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy ID: " + id));
            return;
        }

        if (name != null) c.setFullName(name);
        if (phone != null) c.setPhone(phone);
        if (cccd != null) c.setIdentityCard(cccd);

        if (customerService.updateCustomer(c)) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Cập nhật thành công!"));
        } else {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Cập nhật thất bại!"));
        }
    }

    private void handleDelete(HttpExchange exchange, int id) throws Exception {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        Customer c = customerService.getCustomerById(id);
        if (c == null) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy khách hàng ID: " + id));
            return;
        }

        if (customerService.deleteCustomer(id)) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Xóa khách hàng thành công!"));
        } else {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Không thể xóa khách hàng này (Có thể do đang có lịch sử đặt phòng)"));
        }
    }

}
