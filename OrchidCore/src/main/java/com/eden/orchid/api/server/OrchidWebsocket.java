package com.eden.orchid.api.server;

import com.caseyjbrooks.clog.Clog;
import com.eden.orchid.Orchid;
import com.eden.orchid.api.OrchidContext;
import fi.iki.elonen.NanoWSD;
import lombok.Getter;

import java.io.IOException;
import java.util.EventListener;

@Getter
public class OrchidWebsocket extends NanoWSD implements EventListener {

    private OrchidContext context;
    private WebSocket webSocket;

    private int timeoutMinutes = 30;

    public OrchidWebsocket(OrchidContext context, int port) throws IOException {
        super(ServerUtils.getNearestFreePort(port));
        this.context = context;

        start(timeoutMinutes * 60 * 1000, true);
        System.out.println("\nWebsocket running! Point your browsers to http://localhost:" + getListeningPort() + "/ \n");
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        this.webSocket = new WebsocketHandler(handshake);
        return this.webSocket;
    }

    public void sendMessage(String message) {
        try {
            if(webSocket != null && webSocket.isOpen()) {
                webSocket.send(message);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class WebsocketHandler extends WebSocket {
        public WebsocketHandler(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        protected void onOpen() {
            Clog.d("Opened");
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            Clog.d("Closed [#{$1}] #{$2}#{$3}", new Object[]{
                    (initiatedByRemote ? "Remote" : "Self"),
                    (code != null ? code : "UnknownCloseCode[" + code + "]"),
                    (reason != null && !reason.isEmpty() ? ": " + reason : "")
            });
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            try {
                message.setUnmasked();

                if(message.getTextPayload().equalsIgnoreCase("exit")) {
                    context.broadcast(Orchid.Events.END_SESSION);
                }
                else if(message.getTextPayload().equalsIgnoreCase("rebuild")) {
                    context.broadcast(Orchid.Events.FORCE_REBUILD);
                }

                sendFrame(message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            Clog.d("Pong " + pong);
        }

        @Override
        protected void onException(IOException exception) {
            Clog.e("Exception", exception);
        }

        @Override
        protected void debugFrameReceived(WebSocketFrame frame) {
            Clog.d("Received " + frame);
        }

        @Override
        protected void debugFrameSent(WebSocketFrame frame) {
            Clog.d("Sent " + frame);
        }
    }
}