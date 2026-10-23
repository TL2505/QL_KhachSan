package quanlykhachsan.backend.auth.dto;

public class AuthResponse {
    private String status;
    private String message;
    private Object data;
    private java.util.Map<String, String> fieldErrors;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public java.util.Map<String, String> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(java.util.Map<String, String> fieldErrors) { this.fieldErrors = fieldErrors; }
}
