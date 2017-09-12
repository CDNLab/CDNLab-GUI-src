// ***************************************************************************
//
// HttpTools Project
//
// This file is a part of the HttpTools project. The project was created at
// Reykjavik University, the Laboratory for Dependable Secure Systems (LDSS).
// Its purpose is to create a set of OMNeT++ components to simulate browsing
// behaviour in a high-fidelity manner along with a highly configurable
// Web server component.
//
// Maintainer: Kristjan V. Jonsson (LDSS) kristjanvj@gmail.com
// Project home page: code.google.com/p/omnet-httptools
//
// ***************************************************************************
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License version 3
// as published by the Free Software Foundation.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// ***************************************************************************

#include "HttpCDNServerBase.h"


void HttpCDNServerBase::initialize()
{
    ll = par("logLevel");

    EV_DEBUG << "Initializing server component\n";

    hostName = (const char*)par("hostName");
    if (hostName.empty())
    {
        hostName = "www.";
        hostName += getParentModule()->getFullName();
        hostName += ".com";
    }
    EV_DEBUG << "Initializing HTTP server. Using WWW name " << hostName << endl;
    port = par("port");

    logFileName = (const char*)par("logFile");
    enableLogging = logFileName!="";
    outputFormat = lf_short;

    httpProtocol = par("httpProtocol");

    cXMLElement *rootelement = par("config").xmlValue();
    if (rootelement==NULL)
        error("Configuration file is not defined");

    // Initialize the distribution objects for random browsing
    // @todo Skip initialization of random objects for scripted servers?
    cXMLAttributeMap attributes;
    cXMLElement *element;
    rdObjectFactory rdFactory;

    // The reply delay
    element = rootelement->getFirstChildWithTag("replyDelay");
    if (element==NULL)
        error("Reply delay parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdReplyDelay = rdFactory.create(attributes);
    if (rdReplyDelay==NULL)
        error("Reply delay random object could not be created");

    // HTML page size
    element = rootelement->getFirstChildWithTag("htmlPageSize");
    if (element==NULL)
        error("HTML page size parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdHtmlPageSize = rdFactory.create(attributes);
    if (rdHtmlPageSize==NULL)
        error("HTML page size random object could not be created");

    // Text resource size
    element = rootelement->getFirstChildWithTag("textResourceSize");
    if (element==NULL)
        error("Text resource size parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdTextResourceSize = rdFactory.create(attributes);
    if (rdTextResourceSize==NULL)
        error("Text resource size random object could not be created");

    // Image resource size
    element = rootelement->getFirstChildWithTag("imageResourceSize");
    if (element==NULL)
        error("Image resource size parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdImageResourceSize = rdFactory.create(attributes);
    if (rdImageResourceSize==NULL)
        error("Image resource size random object could not be created");

    // Number of resources per page
    element = rootelement->getFirstChildWithTag("numResources");
    if (element==NULL)
        error("Number of resources parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdNumResources = rdFactory.create(attributes);
    if (rdNumResources==NULL)
        error("Number of resources random object could not be created");

    // Text/Image resources ratio
    element = rootelement->getFirstChildWithTag("textImageResourceRatio");
    if (element==NULL)
        error("Text/image resource ratio parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdTextImageResourceRatio = rdFactory.create(attributes);
    if (rdTextImageResourceRatio==NULL)
        error("Text/image resource ratio random object could not be created");

    // Error message size
    element = rootelement->getFirstChildWithTag("errorMessageSize");
    if (element==NULL)
        error("Error message size parameter undefined in XML configuration");
    attributes = element->getAttributes();
    rdErrorMsgSize = rdFactory.create(attributes);
    if (rdErrorMsgSize==NULL)
        error("Error message size random object could not be created");

    activationTime = par("activationTime");
    EV_INFO << "Activation time is " << activationTime << endl;

    /*std::string siteDefinition = (const char*)par("siteDefinition");
    scriptedMode = !siteDefinition.empty();
    if (scriptedMode)
        readSiteDefinition(siteDefinition);*/

    // Register the server with the controller object
    //registerWithController();

    // Initialize statistics
    htmlDocsServed = imgResourcesServed = textResourcesServed = badRequests = totalSizeServed = 0;
    // Initialize watches
    WATCH(htmlDocsServed);
    WATCH(imgResourcesServed);
    WATCH(textResourcesServed);
    WATCH(badRequests);
    WATCH(totalSizeServed);

    updateDisplay();
}

void HttpCDNServerBase::finish()
{
    EV_SUMMARY << "\n-----------------------------------------------------------\n";
    EV_SUMMARY << "HTML documents served " << htmlDocsServed << "\n";
    EV_SUMMARY << "Image resources served " << imgResourcesServed << "\n";
    EV_SUMMARY << "Text resources served " << textResourcesServed << "\n";
    EV_SUMMARY << "Bad requests " << badRequests << "\n";
    EV_SUMMARY << "Total amount of data served " << totalSizeServed << "\n";

    recordScalar("HTML.served", htmlDocsServed);
    recordScalar("images.served", imgResourcesServed);
    recordScalar("text.served", textResourcesServed);
    recordScalar("bad.requests", badRequests);
    recordScalar("SIZE.served", totalSizeServed);
}

void HttpCDNServerBase::updateDisplay()
{
    if (ev.isGUI())
    {
        char buf[1024];
        sprintf(buf, "%ld", htmlDocsServed);
        getParentModule()->getDisplayString().setTagArg("t", 0, buf);

        if (activationTime<=simTime())
        {
            getParentModule()->getDisplayString().setTagArg("i2", 0, "status/up");
            getParentModule()->getDisplayString().setTagArg("i2", 1, "green");
        }
        else
        {
            getParentModule()->getDisplayString().setTagArg("i2", 0, "status/down");
            getParentModule()->getDisplayString().setTagArg("i2", 1, "red");
        }
    }
}

void HttpCDNServerBase::handleMessage(cMessage *msg)
{
    // Override in derived classes
    updateDisplay();
}


HttpReplyMessage* HttpCDNServerBase::generateDocument(HttpRequestMessage *request,
        const char* resource, int size) {
    EV_DEBUG << "Generating HTML document for request " << request->getName()
                    << " from " << request->getSenderModule()->getName()
                    << endl;

    char szReply[512];
    sprintf(szReply, "HTTP/1.1 200 OK (%s)", resource);
    HttpReplyMessage* replymsg = new HttpReplyMessage(szReply);
    replymsg->setHeading("HTTP/1.1 200 OK");
    replymsg->setOriginatorUrl(hostName.c_str());
    replymsg->setTargetUrl(request->originatorUrl());
    replymsg->setProtocol(request->protocol());
    replymsg->setSerial(request->serial());
    replymsg->setResult(200);
    replymsg->setContentType(CT_HTML); // Emulates the content-type header field
    replymsg->setKind(HTTPT_RESPONSE_MESSAGE);

    if (scriptedMode) {
        replymsg->setPayload(htmlPages[resource].body.c_str());
        size = htmlPages[resource].size;
    } else {
        replymsg->setPayload(generateBody().c_str());
    }

    if (size == 0) {
        EV_DEBUG << "Using random distribution for page size" << endl;
        size = (int) rdHtmlPageSize->draw();
    }

    replymsg->setByteLength(size);
    EV_DEBUG << "Serving a HTML document of length "
                    << replymsg->getByteLength() << " bytes" << endl;

    htmlDocsServed++;
    totalSizeServed=totalSizeServed+size;

    return replymsg;
}

HttpReplyMessage* HttpCDNServerBase::generateResourceMessage(
        HttpRequestMessage *request, std::string resource,
        HttpContentType category) {
    EV_DEBUG << "Generating resource message in response to request "
                    << request->heading() << " with serial "
                    << request->serial() << endl;

    if (category == CT_TEXT)
        textResourcesServed++;
    else if (category == CT_IMAGE)
        imgResourcesServed++;

    char szReply[512];
    sprintf(szReply, "HTTP/1.1 200 OK (%s)", resource.c_str());
    HttpReplyMessage* replymsg = new HttpReplyMessage(szReply);
    replymsg->setHeading("HTTP/1.1 200 OK");
    replymsg->setOriginatorUrl(hostName.c_str());
    replymsg->setTargetUrl(request->originatorUrl());
    replymsg->setProtocol(request->protocol()); // MIGRATE40: kvj
    replymsg->setSerial(request->serial());
    replymsg->setResult(200);
    replymsg->setContentType(category); // Emulates the content-type header field
    replymsg->setByteLength(resources[resource]); // Set the resource size
    replymsg->setKind(HTTPT_RESPONSE_MESSAGE);

    sprintf(szReply, "RESOURCE-BODY:%s", resource.c_str());
    return replymsg;
}


std::string HttpCDNServerBase::generateBody() {
    int numResources = (int) rdNumResources->draw();
    int numImages = (int) (numResources * rdTextImageResourceRatio->draw());
    int numText = numResources - numImages;

    std::string result;

    char tempBuf[128];
    for (int i = 0; i < numImages; i++) {
        sprintf(tempBuf, "%s%.4d.%s\n", "IMG", i, "jpg");
        result.append(tempBuf);
    }
    for (int i = 0; i < numText; i++) {
        sprintf(tempBuf, "%s%.4d.%s\n", "TEXT", i, "txt");
        result.append(tempBuf);
    }

    return result;
}

HttpReplyMessage* HttpCDNServerBase::generateErrorReply(HttpRequestMessage *request,
        int code) {
    char szErrStr[32];
    sprintf(szErrStr, "HTTP/1.1 %.3d %s", code, htmlErrFromCode(code).c_str());
    HttpReplyMessage* replymsg = new HttpReplyMessage(szErrStr);
    replymsg->setHeading(szErrStr);
    replymsg->setOriginatorUrl(hostName.c_str());
    replymsg->setTargetUrl(request->originatorUrl());
    replymsg->setProtocol(request->protocol()); // MIGRATE40: kvj
    replymsg->setSerial(request->serial());
    replymsg->setResult(code);
    replymsg->setByteLength((int) rdErrorMsgSize->draw());
    replymsg->setKind(HTTPT_RESPONSE_MESSAGE);

    badRequests++;
    return replymsg;
}

//sjj
void HttpCDNServerBase::registerSurrogateWithController()
{
    // Find controller object and register

    cModule * controller = simulation.getSystemModule()->getSubmodule("controller");
    if (controller == NULL)
        error("Controller module not found");
    //location info
    LocationInfo locationInfo;
    locationInfo.IP_address_on_web=this->par("IP_address_on_web").stringValue();
    locationInfo.latitude=this->par("latitude").doubleValue();
    locationInfo.longitude=this->par("longitude").doubleValue();
    locationInfo.country=this->par("country").stringValue();
    locationInfo.city=this->par("city").stringValue();
    ((CDNController*)controller)->registerSurrogateServer(getParentModule()->getFullName(), hostName.c_str(), port, locationInfo, INSERT_END, activationTime);
}

void HttpCDNServerBase::registerOriginWithController()
{
    // Find controller object and register

    cModule * controller = simulation.getSystemModule()->getSubmodule("controller");
    if (controller == NULL)
        error("Controller module not found");
    ((CDNController*)controller)->registerOriginServer(getParentModule()->getFullName(), hostName.c_str(), port, INSERT_END, activationTime);
}



