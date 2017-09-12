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

#include "OriginServer.h"

Define_Module(OriginServer);

void OriginServer::initialize() {
    HttpCDNServerBase::initialize();
    HttpCDNServerBase::registerOriginWithController();

    EV_DEBUG << "Initializing origin server component (sockets version)"
                    << endl;

    int port = par("port");

    TCPSocket listensocket;
    listensocket.setOutputGate(gate("tcpOut"));
    listensocket.setDataTransferMode(TCP_TRANSFER_OBJECT);
    listensocket.bind(port);
    listensocket.setCallbackObject(this);
    listensocket.listen();

    std::string siteDefinition = (const char*) par("siteDefinition");
    //scriptedMode = !siteDefinition.empty();
    scriptedMode=true;
    if (scriptedMode)
        readSiteDefinition(siteDefinition);

    numBroken = 0;
    socketsOpened = 0;

    WATCH(numBroken);
    WATCH(socketsOpened);
}

void OriginServer::finish() {
    HttpCDNServerBase::finish();

    EV_SUMMARY << "Sockets opened: " << socketsOpened << endl;
    EV_SUMMARY << "Broken connections: " << numBroken << endl;

    recordScalar("sock.opened", socketsOpened);
    recordScalar("sock.broken", numBroken);

    // Clean up sockets and data structures
    sockCollection.deleteSockets();
}

void OriginServer::handleMessage(cMessage *msg) {
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
            socket->setCallbackObject(this, socket);
            sockCollection.addSocket(socket);
        }
        EV_DEBUG << "Process the message " << msg->getName() << endl;
        socket->processMessage(msg);
    }
    updateDisplay();
}

cPacket* OriginServer::handleReceivedMessage(cMessage *msg) {
    HttpRequestMessage *request = check_and_cast<HttpRequestMessage *>(msg);
    if (request == NULL)
        error("Message (%s)%s is not a valid request", msg->getClassName(),
                msg->getName());

    EV_DEBUG << "Handling received message " << msg->getName()
                    << ". Target URL: " << request->targetUrl() << endl;

    logRequest(request);

    if (extractServerName(request->targetUrl()) != hostName) {
        // This should never happen but lets check
        error("Received message intended for '%s'", request->targetUrl()); // TODO: DEBUG HERE
        return NULL;
    }

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
        replymsg = handleGetRequest(request, res[1]); // Pass in the resource string part
    } else {
        EV_ERROR << "Unsupported request type " << res[0] << " for "
                        << request->heading() << endl;
        replymsg = generateErrorReply(request, 400);
    }

    if (replymsg != NULL) {

        // if the request is coming from surrogate server
        // we should set the request ID here, then on the surrogate server,
        // we can tell this reply message belongs to which socket (client).
        if (request->findPar("RequestSockID") != -1) {
            replymsg->addPar("RequestSockID");
            replymsg->par("RequestSockID").setLongValue(
                    request->par("RequestSockID").longValue());
        }
        //set the request sending time in order to calculate response time
        if (msg->findPar("requestTimeStamp") != -1){
            replymsg->addPar("requestTimeStamp");
            replymsg->par("requestTimeStamp").setDoubleValue(msg->par("requestTimeStamp").doubleValue());
        }
        logResponse(replymsg);
    }

    return replymsg;
}
/*
 * handles a get request
 * No client request is served by origin server directly
 * only surrogate servers fetch contents from the origin server in case of cache miss.
 */

HttpReplyMessage* OriginServer::handleGetRequest(HttpRequestMessage *request,
        std::string resource) {
    EV_DEBUG << "Handling GET request " << request->getName() << " resource: "
                    << resource << endl;

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
                    EV_ERROR << "Page not found: " << resource << endl;
                    return generateErrorReply(request, 404);
                }
            }
        }
        return generateDocument(request, resource.c_str());
    /*} else if (cat == CT_TEXT || cat == CT_IMAGE) {
        if (scriptedMode && resources.find(resource) == resources.end()) {
            EV_ERROR << "Resource not found: " << resource << endl;
            return generateErrorReply(request, 404);
        }
        return generateResourceMessage(request, resource, cat);
    } else {
        EV_ERROR << "Unknown or unsupported resource requested in "
                        << request->heading() << endl;
        return generateErrorReply(request, 400);
    }*/
}

void OriginServer::socketEstablished(int connId, void *yourPtr) {
    EV_INFO << "connected socket with id=" << connId << endl;
    socketsOpened++;
}

void OriginServer::socketDataArrived(int connId, void *yourPtr, cPacket *msg,
        bool urgent) {
    if (yourPtr == NULL) {
        EV_ERROR << "Socket establish failure. Null pointer" << endl;
        return;
    }
    TCPSocket *socket = (TCPSocket*) yourPtr;

    // Should be a HttpReplyMessage
    EV_DEBUG << "OriginServer: Socket data arrived on connection " << connId
                    << ". Message=" << msg->getName() << ", kind="
                    << msg->getKind() << endl;
    EV_DEBUG << "OriginServer: SENDER MODULE ID " << msg->getSenderModuleId()
                    << " " << msg->getSenderModule()->getName() << endl;

    // call the message handler to process the message.
    cMessage *reply = handleReceivedMessage(msg);
    if (reply != NULL) {
        //
        reply->addPar("ORIGIN_DATA");
        socket->send(reply); // Send to socket if the reply is non-zero.
    }
    delete msg; // Delete the received message here. Must not be deleted in the handler!
}

void OriginServer::socketPeerClosed(int connId, void *yourPtr) {
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

void OriginServer::socketClosed(int connId, void *yourPtr) {
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

void OriginServer::socketFailure(int connId, void *yourPtr, int code) {
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

void OriginServer::readSiteDefinition(std::string file) {
    EV_DEBUG << "Reading site definition file " << file << endl;

    std::ifstream tracefilestream;
    tracefilestream.open(file.c_str());
    if (tracefilestream.fail())
        error("Could not open site definition file %s", file.c_str());

    std::vector<std::string> siteFileSplit = splitFile(file);
    std::string line;
    std::string key;
    std::string htmlfile;
    std::string body;
    std::string value1;
    std::string value2;
    std::string sectionsub;
    int size;
    int linecount = 0;
    bool siteSection = false;
    bool resourceSection = false;

    while (!std::getline(tracefilestream, line).eof()) {
        linecount++;
        line = trim(line);
        if (line.empty() || line[0] == '#')
            continue;
        sectionsub = getDelimited(line, "[", "]");
        if (!sectionsub.empty()) {
            // Section
            siteSection = sectionsub == "HTML";
            resourceSection = sectionsub == "RESOURCES";
        } else {
            cStringTokenizer tokenizer = cStringTokenizer(line.c_str(), ";");
            std::vector<std::string> res = tokenizer.asVector();

            if (siteSection) {
                if (res.size() < 2 || res.size() > 3)
                    error(
                            "Invalid format of site configuration file '%s'. Site section, line (%d): %s",
                            file.c_str(), linecount, line.c_str());
                key = trimLeft(res[0], "/");
                if (key.empty()) {
                    if (htmlPages.find("root") == htmlPages.end())
                        key = "root";
                    else
                        error(
                                "Second root page found in site definition file %s, line (%d): %s",
                                file.c_str(), linecount, line.c_str());
                }
                htmlfile = res[1];
                body = readHtmlBodyFile(htmlfile, siteFileSplit[0]); // Pass in the path of the definition file. Page defs are relative to that.
                size = 0;
                if (res.size() > 2) {
                    try {
                        size = atoi(res[2].c_str());
                    } catch (...) {
                        error(
                                "Invalid format of site configuration file '%s'. Resource section, size, line (%d): %s",
                                file.c_str(), linecount, line.c_str());
                    }
                }
                EV_DEBUG << "Adding html page definition " << key
                                << ". The page size is " << size << endl;
                htmlPages[key].size = size;
                htmlPages[key].body = body;
            } else if (resourceSection) {
                if (res.size() < 2 || res.size() > 3)
                    error(
                            "Invalid format of site configuration file '%s'. Resource section, line (%d): %s",
                            file.c_str(), linecount, line.c_str());
                key = res[0];
                value1 = res[1];
                try {
                    size = atoi(value1.c_str());
                } catch (...) {
                    error(
                            "Invalid format of site configuration file '%s'. Resource section, size, line (%d): %s",
                            file.c_str(), linecount, line.c_str());
                }

                if (res.size() > 2) {
                    // The type parameter - skip this
                }

                resources[key] = size;
                EV_DEBUG << "Adding resource " << key << " of size " << size
                                << endl;
            } else {
                error(
                        "Invalid format of site configuration file '%s'. Unknown section, line (%d): %s",
                        file.c_str(), linecount, line.c_str());
            }
        }
    }
    tracefilestream.close();
}

std::string OriginServer::readHtmlBodyFile(std::string file, std::string path) {
    EV_DEBUG << "Reading HTML page definition file" << endl;

    std::string filePath = path;
    filePath += file;

    std::string line;
    std::string body = "";
    std::ifstream htmlfilestream;
    htmlfilestream.open(filePath.c_str());
    if (htmlfilestream.fail())
        error("Could not open page definition file '%s'", filePath.c_str());
    while (!std::getline(htmlfilestream, line).eof()) {
        line = trim(line);
        if (line.empty() || line[0] == '#')
            continue;
        body += line;
        body += "\n";
    }
    htmlfilestream.close();
    return body;
}
