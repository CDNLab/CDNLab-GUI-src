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

#ifndef __INET_ORIGINSERVERSOCK_H
#define __INET_ORIGINSERVERSOCK_H

#include <omnetpp.h>
#include "TCPSocket.h"
#include "TCPSocketMap.h"
#include "HttpCDNServerBase.h"

/**
 * TODO - Generated class
 */
class INET_API OriginServer: public HttpCDNServerBase,
        public TCPSocket::CallbackInterface {
protected:
    TCPSocket listensocket;
    TCPSocketMap sockCollection;
    unsigned long numBroken;
    unsigned long socketsOpened;

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


    /** Handle a received HTTP GET request */
    HttpReplyMessage* handleGetRequest(HttpRequestMessage *request,
            std::string resource);
    /** Read a site definition from file if a scripted site definition is used. */
    void readSiteDefinition(std::string file);
    /** Read a html body from a file. Used by readSiteDefinition. */
    std::string readHtmlBodyFile(std::string file, std::string path);
    /** Handle a received data message, e.g. check if the content requested exists. */
    cPacket* handleReceivedMessage(cMessage *msg);
};

#endif /*__INET_ORIGINSERVERSOCK_H*/
