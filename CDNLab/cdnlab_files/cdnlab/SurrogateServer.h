//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/.
// 

#ifndef __INET_SurrogateServerSOCK_H
#define __INET_SurrogateServerSOCK_H

#include <omnetpp.h>
#include "TCPSocket.h"
#include "TCPSocketMap.h"
#include "HttpCDNServerBase.h"
#include "IPvXAddressResolver.h"
/**
 * TODO - Generated class
 */
class INET_API SurrogateServer: public HttpCDNServerBase,
        public TCPSocket::CallbackInterface {

public:
    virtual ~SurrogateServer();

protected:
    TCPSocket listensocket;
    TCPSocketMap sockCollection;
    unsigned long numBroken;
    unsigned long socketsOpened;
    // Basic statistics
    long cacheMisses;

    /**
     * A list of HTTP requests to send.
     */
    typedef std::deque<HttpRequestMessage*> HttpRequestQueue;
    /**
     * Data structure used to keep state for each opened socket.
     *
     * An instance of this struct is created for each opened socket and assigned to
     * it as a myPtr. See the TCPSocket::CallbackInterface methods of HttpCDNBrowser for more
     * details.
     */
    struct SockData {
        HttpRequestQueue messageQueue; ///< Queue of pending messages.
        TCPSocket *socket; ///< A reference to the socket object.
        int pending; ///< A counter for the number of outstanding replies.
    };

protected:
    /** @name cSimpleModule redefinitions */
    //@{
    /** Initialization of the component and startup of browse event scheduling */
    virtual void initialize();

    /** Report final statistics */
    virtual void finish();

    /** Handle incoming messages */
    virtual void handleMessage(cMessage *msg);
    //@}

protected:
    /** @name TCPSocket::CallbackInterface methods */
    //@{
    /**
     * Handler for socket established events.
     * Only used to update statistics.
     */
    virtual void socketEstablished(int connId, void *yourPtr);

    /**
     * Handler for socket data arrived events.
     * Dispatches the received message to the message handler in the base class and
     * finishes by deleting the received message.
     */
    virtual void socketDataArrived(int connId, void *yourPtr, cPacket *msg,
            bool urgent);

    /**
     * Handler for socket closed by peer event.
     * Does little apart from calling socket->close() to allow the TCPSocket object to close properly.
     */
    virtual void socketPeerClosed(int connId, void *yourPtr);

    /**
     * Handler for socket closed event.
     * Cleanup the resources for the closed socket.
     */
    virtual void socketClosed(int connId, void *yourPtr);

    /**
     * Handler for socket failure event.
     * Very basic handling -- displays warning and cleans up resources.
     */
    virtual void socketFailure(int connId, void *yourPtr, int code);

    /**
     * send request to origin server
     * this will be called when the content does not exist in the surrogates cache
     */
    virtual void sendRequestToOriginServer(HttpRequestMessage *request);

    /**
     * Establishes a socket and queues a single message for transmission.
     * A new socket is created and added to the collection. The message is assigned to a data structure
     * stored as a myPtr with the socket. The message is transmitted once the socket is established, signaled
     * by a call to socketEstablished.
     */
    void submitToSocket(const char* moduleName, int connectPort,
            HttpRequestMessage *msg);

    /**
     * Establishes a socket and assigns a queue of messages to be transmitted.
     * Same as the overloaded version, except a number of messages are queued for transmission. The same socket
     * instance is used for all the queued messages.
     */
    void submitToSocket(const char* moduleName, int connectPort,
            HttpRequestQueue &queue);
    /** Handle a received data message, e.g. check if the content requested exists. */
    cPacket* handleReceivedMessage(cMessage *msg);
    /** store the received message from origin server into surrogate server's cache   */
    void cacheReceivedMessageFromOriginServer(cMessage *msg);
    /** Handle a received HTTP GET request */
    HttpReplyMessage* handleGetRequest(HttpRequestMessage *request,
            std::string resource);
    //@}

    /** Generate a No Content Reply Message in response to a request. */
    HttpReplyMessage* generateNoContentReplyMessage(HttpRequestMessage *request,
            const char* resource, int size = 0);
};

#endif /*__INET_SurrogateServerSOCK_H*/
