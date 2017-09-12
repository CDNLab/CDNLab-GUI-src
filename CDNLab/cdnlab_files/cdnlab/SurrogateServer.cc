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

#include "SurrogateServer.h"

Define_Module(SurrogateServer);

void SurrogateServer::initialize() {
    HttpCDNServerBase::initialize();
    HttpCDNServerBase::registerSurrogateWithController();

    EV_DEBUG << "Initializing origin server component (sockets version)"
                    << endl;

    int port = par("port");

    TCPSocket listensocket;
    listensocket.setOutputGate(gate("tcpOut"));
    listensocket.setDataTransferMode(TCP_TRANSFER_OBJECT);
    listensocket.bind(port);
    listensocket.setCallbackObject(this);
    listensocket.listen();

    scriptedMode=true;

    numBroken = 0;
    socketsOpened = 0;
    cacheMisses = 0;
    WATCH(numBroken);
    WATCH(socketsOpened);
    WATCH(cacheMisses);
}

void SurrogateServer::finish() {
    HttpCDNServerBase::finish();
    EV_SUMMARY << "cache misses " << cacheMisses << "\n" << endl;
    recordScalar("cache.missed", cacheMisses);

    EV_SUMMARY << "Sockets opened: " << socketsOpened << endl;
    EV_SUMMARY << "Broken connections: " << numBroken << endl;

    recordScalar("sock.opened", socketsOpened);
    recordScalar("sock.broken", numBroken);

    // Clean up sockets and data structures
    sockCollection.deleteSockets();
}

void SurrogateServer::handleMessage(cMessage *msg) {
    if (msg->isSelfMessage()) {
        // Self messages not used at the moment
    } else {
        EV_DEBUG << "Handle inbound message " << msg->getName() << " of kind "
                        << msg->getKind() << endl;
        TCPSocket *socket = sockCollection.findSocketFor(msg);
        if (!socket) {
            EV_DEBUG << "No socket found for the message. Create a new one"
                            << endl;
            // new connection -- create new socket object and server process
            socket = new TCPSocket(msg);
            socket->setOutputGate(gate("tcpOut"));
            socket->setDataTransferMode(TCP_TRANSFER_OBJECT);

            //
            // Initialize the associated data structure
            SockData *sockdata = new SockData;
            //sockdata->messageQueue = NULL;
            sockdata->socket = socket;
            sockdata->pending = 0;
            socket->setCallbackObject(this, sockdata);
            //

            //socket->setCallbackObject(this, socket);
            sockCollection.addSocket(socket);
        }
        EV_DEBUG << "Process the message " << msg->getName() << endl;
        socket->processMessage(msg);
    }
    updateDisplay();
}

void SurrogateServer::socketEstablished(int connId, void *yourPtr) {
    EV_DEBUG << "Socket with id " << connId << " established" << endl;

    socketsOpened++;

    if (yourPtr == NULL) {
        EV_ERROR << "SocketEstablished failure. Null pointer" << endl;
        return;
    }

    // Get the socket and associated data structure.
    SockData *sockdata = (SockData*) yourPtr;
    TCPSocket *socket = sockdata->socket;
    if (sockdata->messageQueue.empty()) {
        EV_INFO
                       <<
                       "No data to send on socket with connection id for origin server!"
                       << endl;
        //socket->close();
        return;
    }

    // Send pending messages on the established socket.
    cMessage *msg;
    EV_DEBUG << "Proceeding to send messages on socket " << connId << endl;

    while (!sockdata->messageQueue.empty()) {
        msg = sockdata->messageQueue.back();

        cPacket *pckt = check_and_cast<cPacket *>(msg);
        sockdata->messageQueue.pop_back();
        EV_DEBUG << "Submitting request " << msg->getName() << " to socket "
                        << connId << ". size is " << pckt->getByteLength()
                        << " bytes" << endl;
        if (msg->getControlInfo())
            delete msg->removeControlInfo();

        //
        socket->send(msg);
        sockdata->pending++;
    }

}

void SurrogateServer::socketDataArrived(int connId, void *yourPtr, cPacket *msg,
        bool urgent) {
    if (yourPtr == NULL) {
        EV_ERROR << "Socket establish failure. Null pointer" << endl;
        return;
    }
    SockData *sockdata = (SockData*) yourPtr;
    TCPSocket *socket = sockdata->socket;
    //TCPSocket *socket = (TCPSocket*) yourPtr;

    EV_DEBUG << "SurrogateServer: Socket data arrived on connection " << connId
                    << ". Message=" << msg->getName() << ", kind="
                    << msg->getKind() << endl;
    EV_DEBUG << "SurrogateServer: SENDER MODULE ID " << msg->getSenderModuleId()
                    << endl;

    cMessage *reply = NULL;
    // data is coming from origin server
    //it should be redirected to the corresponding client
    if (msg->findPar("ORIGIN_DATA") != -1) {
        reply = check_and_cast<HttpReplyMessage *>(msg);
        //isFromOriginServer=true;

        cacheReceivedMessageFromOriginServer(reply);

        //find the corresponding socket to redirect the reply toward the client
        long socketID = reply->par("RequestSockID").longValue();
        cMessage *dummyMessage = new HttpBaseMessage();
        TCPSendCommand *cmd = new TCPSendCommand();
        cmd->setConnId(socketID);
        dummyMessage->setControlInfo(cmd);
        socket = sockCollection.findSocketFor(dummyMessage);
        delete dummyMessage;
        htmlDocsServed++;
        totalSizeServed=totalSizeServed+((HttpReplyMessage *)reply)->getByteLength();
        if (msg->getControlInfo())
            delete msg->removeControlInfo();

    } else {
        // data is coming from a client
        /**
         * write the socket as a parameter to send to origin server
         * this is used when a request is redirected to origin server
         * when the reply comes back, we are able to send it back to the client using this parameter.
         */
        msg->addPar("RequestSockID");
        msg->par("RequestSockID").setLongValue(socket->getConnectionId());
        reply = SurrogateServer::handleReceivedMessage(msg);
    }
    if (reply != NULL) {
        //set the request sending time in order to calculate response time
        if (msg->findPar("requestTimeStamp") != -1){
            reply->addPar("requestTimeStamp");
            reply->par("requestTimeStamp").setDoubleValue(msg->par("requestTimeStamp").doubleValue());
        }
        socket->send(reply); // Send to socket if the reply is non-zero.
    }
    //HttpReplyMessage *tmp_reply = check_and_cast<HttpReplyMessage *>(reply);
    if (reply->findPar("NoContent") == -1 && msg->findPar("ORIGIN_DATA") == -1){
        //msg->getOwner()->drop(msg);
        //reply->removeObject("NoContent");
        delete msg; // Delete the received message here. Must not be deleted in the handler!
    }
}

void SurrogateServer::socketPeerClosed(int connId, void *yourPtr) {
    if (yourPtr == NULL) {
        EV_ERROR << "Socket establish failure. Null pointer" << endl;
        return;
    }
    TCPSocket *socket = (TCPSocket*) yourPtr;

    // close the connection (if not already closed)
    if (socket->getState() == TCPSocket::PEER_CLOSED) {
        EV_INFO << "remote TCP closed, closing here as well. Connection id is "
                       << connId << endl;
        socket->close(); // Call the close method to properly dispose of the socket.
    }
}

void SurrogateServer::socketClosed(int connId, void *yourPtr) {
    EV_INFO << "connection closed. Connection id " << connId << endl;

    if (yourPtr == NULL) {
        EV_ERROR << "Socket establish failure. Null pointer" << endl;
        return;
    }
    // Cleanup
    TCPSocket *socket = (TCPSocket*) yourPtr;
    sockCollection.removeSocket(socket);
    delete socket;
}

void SurrogateServer::socketFailure(int connId, void *yourPtr, int code) {
    EV_WARNING << "connection broken. Connection id " << connId << endl;
    numBroken++;

    EV_INFO << "connection closed. Connection id " << connId << endl;

    if (yourPtr == NULL) {
        EV_ERROR << "Socket establish failure. Null pointer" << endl;
        return;
    }
    TCPSocket *socket = (TCPSocket*) yourPtr;

    if (code == TCP_I_CONNECTION_RESET)
        EV_WARNING << "Connection reset!\n";
    else if (code == TCP_I_CONNECTION_REFUSED)
        EV_WARNING << "Connection refused!\n";

    // Cleanup
    sockCollection.removeSocket(socket);
    delete socket;
}

cPacket* SurrogateServer::handleReceivedMessage(cMessage *msg) {
    HttpRequestMessage *request = check_and_cast<HttpRequestMessage *>(msg);
    if (request == NULL)
        error("Message (%s)%s is not a valid request", msg->getClassName(),
                msg->getName());

    EV_DEBUG << "Handling received message " << msg->getName()
                    << ". Target URL: " << request->targetUrl() << endl;

    logRequest(request);

    HttpReplyMessage* replymsg;

    // Parse the request string on spaces
    cStringTokenizer tokenizer = cStringTokenizer(request->heading(), " ");
    std::vector<std::string> res = tokenizer.asVector();
    if (res.size() != 3) {
        EV_ERROR << "Invalid request string: " << request->heading() << endl;
        replymsg = generateErrorReply(request, 400);
        logResponse(replymsg);
        return replymsg;
    }

    if (request->badRequest()) {
        // Bad requests get a 404 reply.
        EV_ERROR << "Bad request - bad flag set. Message: "
                        << request->getName() << endl;
        replymsg = generateErrorReply(request, 404);
    } else if (res[0] == "GET") {
        replymsg = SurrogateServer::handleGetRequest(request, res[1]); // Pass in the resource string part
    } else {
        EV_ERROR << "Unsupported request type " << res[0] << " for "
                        << request->heading() << endl;
        replymsg = generateErrorReply(request, 400);
    }

    if (replymsg != NULL)
        logResponse(replymsg);

    return replymsg;
}

HttpReplyMessage* SurrogateServer::handleGetRequest(HttpRequestMessage *request,
        std::string resource) {
    EV_DEBUG << "Handling GET request on surrogate server" << request->getName()
                    << " resource: " << resource << endl;

    resource = trimLeft(resource, "/");
    std::vector<std::string> req = parseResourceName(resource);
    if (req.size() != 3) {
        EV_ERROR << "Invalid GET request string: " << request->heading()
                        << endl;
        return generateErrorReply(request, 400);
    }

    HttpContentType cat = getResourceCategory(req);

    //if (cat == CT_HTML) {
        if (scriptedMode) {
            if (resource.empty() && htmlPages.find("root") != htmlPages.end()) {
                EV_DEBUG << "Generating root resource" << endl;
                return generateDocument(request, "root");
            }
            if (htmlPages.find(resource) == htmlPages.end()) {
                if (htmlPages.find("default") != htmlPages.end()) {
                    EV_DEBUG << "Generating default resource" << endl;
                    return generateDocument(request, "default");
                } else {
                    EV_ERROR
                                    <<
                                    "Page not found on surrogate: "
                                    << resource
                                    << ", it should be fetched from the origin server"
                                    << endl;
                    //return generateErrorReply(request, 404);
                    SurrogateServer::sendRequestToOriginServer(request);
                    return generateNoContentReplyMessage(request,
                            resource.c_str());
                }
            }
        }
        return generateDocument(request, resource.c_str());
    /*} else if (cat == CT_TEXT || cat == CT_IMAGE) {
        if (scriptedMode && resources.find(resource) == resources.end()) {
            EV_ERROR << "Resource not found on surrogate: "
                            << ", it should be fetched from the origin server"
                            << resource << endl;
            //return generateErrorReply(request, 404);
            SurrogateServer::sendRequestToOriginServer(request);
            return generateNoContentReplyMessage(request, resource.c_str());
        }
        return generateResourceMessage(request, resource, cat);
    } else {
        EV_ERROR << "Unknown or unsupported resource requested in "
                        << request->heading() << endl;
        return generateErrorReply(request, 400);
    }*/
}

HttpReplyMessage* SurrogateServer::generateNoContentReplyMessage(
        HttpRequestMessage *request, const char* resource, int size) {
    EV_DEBUG << "Generating No Content Reply Message for request "
                    << request->getName() << " from "
                    << request->getSenderModule()->getName() << endl;

    char szReply[512];
    sprintf(szReply, "HTTP/1.1 204 No Content (%s)", resource);
    HttpReplyMessage* replymsg = new HttpReplyMessage(szReply);
    replymsg->setHeading("HTTP/1.1 204 No Content");
    replymsg->setOriginatorUrl(hostName.c_str());
    replymsg->setTargetUrl(request->originatorUrl());
    replymsg->setProtocol(request->protocol());
    replymsg->setSerial(request->serial());
    replymsg->setResult(204);
    replymsg->setContentType(CT_HTML); // Emulates the content-type header field
    replymsg->setKind(HTTPT_RESPONSE_MESSAGE);
    replymsg->addPar("NoContent");
    replymsg->par("NoContent").setDoubleValue(1);
    /*if (scriptedMode) {
     replymsg->setPayload(htmlPages[resource].body.c_str());
     size = htmlPages[resource].size;
     } else {
     replymsg->setPayload(generateBody().c_str());
     }*/

    if (size == 0) {
        EV_DEBUG << "Using random distribution for page size" << endl;
        size = (int) rdHtmlPageSize->draw();
    }

    replymsg->setByteLength(size);
    EV_DEBUG << "Serving a HTML document of length "
                    << replymsg->getByteLength() << " bytes" << endl;

    cacheMisses++;

    return replymsg;
}

/*
 * send a request to the origin server
 */
void SurrogateServer::sendRequestToOriginServer(HttpRequestMessage *request) {
    int connectPort;
    char szModuleName[127];
    cModule * controller = simulation.getSystemModule()->getSubmodule(
            "controller");
    if (controller == NULL)
        error("Controller module not found");

    if (((CDNController*) controller)->getOriginServerInfo(request->targetUrl(),
            szModuleName, connectPort) != 0) {
        EV_ERROR << "Unable to get server info for URL " << request->targetUrl()
                        << endl;
        return;
    }

    EV_DEBUG << "Sending request to the origin server " << request->targetUrl()
                    << " (" << szModuleName << ") on port " << connectPort
                    << endl;
    submitToSocket(szModuleName, connectPort, request);
}

void SurrogateServer::submitToSocket(const char* moduleName, int connectPort,
        HttpRequestMessage *msg) {
    // Create a queue and push the single message
    HttpRequestQueue queue;
    queue.push_back(msg);
    // Call the overloaded version with the queue as parameter
    submitToSocket(moduleName, connectPort, queue);
}

void SurrogateServer::submitToSocket(const char* moduleName, int connectPort,
        HttpRequestQueue &queue) {
    // Dont do anything if the queue is empty.s
    if (queue.empty()) {
        EV_INFO << "Submitting to socket. No data to send to " << moduleName
                       << ". Skipping connection." << endl;
        return;
    }

    EV_DEBUG << "Submitting to socket. Module: " << moduleName << ", port: "
                    << connectPort << ". Total messages: " << queue.size()
                    << endl;

    // Create and initialize the socket
    TCPSocket *socket = new TCPSocket();
    socket->setDataTransferMode(TCP_TRANSFER_OBJECT);
    socket->setOutputGate(gate("tcpOut"));
    sockCollection.addSocket(socket);

    // Initialize the associated data structure
    SockData *sockdata = new SockData;
    sockdata->messageQueue = HttpRequestQueue(queue);
    sockdata->socket = socket;
    sockdata->pending = 0;
    socket->setCallbackObject(this, sockdata);

    // Issue a connect to the socket for the specified module and port.
    socket->connect(IPvXAddressResolver().resolve(moduleName), connectPort);
}

void SurrogateServer::cacheReceivedMessageFromOriginServer(cMessage *msg) {
    HttpReplyMessage *reply = check_and_cast<HttpReplyMessage *>(msg);
    // Parse the reply string on spaces
    cStringTokenizer tokenizer = cStringTokenizer(reply->getName(), " ");
    std::vector<std::string> res = tokenizer.asVector();
    std::string key = res[3];
    key = key.substr(1, key.length() - 2);
    if (reply->contentType() == CT_HTML) {
        htmlPages[key].body = reply->payload();
        htmlPages[key].size = reply->getByteLength();
    } else if (reply->contentType() == CT_IMAGE
            || reply->contentType() == CT_TEXT) {
        resources[key] = reply->getByteLength();
    }
}

SurrogateServer::~SurrogateServer(){
    cOwnedObject *Del=NULL;
    int OwnedSize=this->defaultListSize();
    for(int i=0;i<OwnedSize;i++){
            Del=this->defaultListGet(0);
            this->drop(Del);
            delete Del;
    }
}
