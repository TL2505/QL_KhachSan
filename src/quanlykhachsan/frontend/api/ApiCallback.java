package quanlykhachsan.frontend.api;

import java.net.http.HttpResponse;

/** Receives completed API work on Swing's Event Dispatch Thread. */
public interface ApiCallback {
    void onSuccess(HttpResponse<String> response);
    void onError(Exception error);
}
