// ***************************************************************************
//
// HttpTools Project
//
// ***************************************************************************

#ifndef __INET_CDNController_H
#define __INET_CDNController_H

#include <string>
#include <list>
#include <vector>
#include <fstream>

#include "INETDefs.h"

#include "HttpLogdefs.h"
#include "HttpUtils.h"
#include "HttpRandom.h"
#include "HttpEventMessages_m.h"
#include "util/CDNLabTypes.h"

// Definitions for the insertion of registered servers into the picklist.
#define INSERT_END 0
#define INSERT_RANDOM -1
#define INSERT_MIDDLE -2

#define STATUS_UPDATE_MSG 0

/**
 */
class CDNController: public cSimpleModule {
protected:
    enum ServerStatus {
        SS_NORMAL, SS_SPECIAL
    };

    /**
     * Registration entry for Web servers
     */
    struct WebServerEntry {
        std::string name; ///< The server URL.
        std::string host; ///< The OMNeT++ object name.
        LocationInfo locationInfo;
        int port; ///< Listening port.
        cModule *module; ///< The actual OMNeT++ module reference to the server object.
        simtime_t activationTime; ///< Activation time for the server relative to simulation start.
        simtime_t statusSetTime; ///< Special status set time.
        ServerStatus serverStatus; ///< The default is SS_NORMAL -- no modification events set.
        double pvalue; ///< Special (elevated) picking probability if SS_SPECIAL is set.
        double pamortize; ///< Amortization factor -- reduces special probability on each hit.
        unsigned long accessCount; ///< A counter for the number of server hits.
        unsigned long totalDeliveredSize; //amount of data served by this server.
        //keep a vector of distances of clients connecting to this server
        //cOutVector distances;
        unsigned long sumOfDistances;
    };

protected:
    int ll; ///< The log level

    //std::map<std::string,WebServerEntry*> webSiteList;  ///< A list of registered web sites (server objects)
    //SJJ
    WebServerEntry* originServerEntry; ///< the origin server
    std::map<std::string, WebServerEntry*> surrogateServersList; ///< A list of registered surrogate web sites (surrogate server objects)
    std::vector<WebServerEntry*> surrogatePickList; ///< The picklist used to select sites at random.
    ///SJJ
    //std::vector<WebServerEntry*> pickList;   ///< The picklist used to select sites at random.
    std::list<WebServerEntry*> specialList; ///< The special list -- contains sites with active popularity modification events.
    double pspecial; ///< The probability [0,1) of selecting a site from the special list.

    unsigned long totalLookups; ///< A counter for the total number of lookups
    unsigned long meanDistances;

    rdObject *rdServerSelection; ///< The random object for the server selection.
    bool cdnMode;
protected:
    /** @name cSimpleModule redefinitions */
    //@{
    /**
     * Initialization of the component and startup of browse event scheduling.
     * Multi-stage is required to properly initialize the object for random site selection after
     * all servers have been registered.
     */
    virtual void initialize(int stage);

    /** Report final statistics */
    virtual void finish();

    /** Handle incoming messages */
    virtual void handleMessage(cMessage *msg);

    /** Returns the number of initialization stages. Two required. */
    int numInitStages() const {
        return 2;
    }
    //@}

public:
    /** @name public interface used by server and browser objects in the simulation */
    //@{
    /**
     * Register a WWW server object.
     * Called by server objects at startup. @see HttpServerBase.
     * A data structure is created for the registered server for easy lookup. It is entered into
     * the site picklist which is used to select servers in the general population. The insertion is specified
     * by the INSERT_* defines: Registered sites can be inserted at the end, in the middle or at random. This does
     * play a role when zipf or other non-uniform selection probability is used (the pick list is basically mapped to
     * the non-uniform distribution).
     */
    //void registerServer(const char* objectName, const char* wwwName, int port, int rank = INSERT_RANDOM, simtime_t activationTime = 0.0);
    //sjj
    void registerOriginServer(const char* objectName, const char* wwwName,
            int port, int rank = INSERT_RANDOM, simtime_t activationTime = 0.0);
    void registerSurrogateServer(const char* objectName, const char* wwwName,
            int port,LocationInfo locationInfo, int rank = INSERT_RANDOM, simtime_t activationTime = 0.0);
    ///sjj

    /**
     * Get a specific server module reference.
     * Returns a OMNeT++ module reference for a specific WWW name. Called by browser modules to get a
     * communications partner. @see HttpBrowserBase
     */
    cModule* getSurrogateServerModule(LocationInfo locationInfo, const char* wwwName);

    /**
     * Get a random server object.
     * Returns a OMNeT++ module reference to a randomly chosen server. The general popularity distribution
     * is used in conjunction with the special list to determine the module returned. Called by browser
     * modules to get a random communications partner. @see HttpBrowserBase
     */
    cModule* getRandomSurrogateServerModule();
    cModule* getOriginServerModule();

    /**
     * Select the nearest surrogate server to a client in order to send request to.
     */
    int selectSurrogateServer_nearest(LocationInfo locationInfo,
            const char* wwwName, char* module, int &port);

    /**
     * Get module and port for a server.
     * Get a module reference and port number for a specific www name. Used by the browser modules
     * when using TCP transport. @see HttpBrowser
     */
    int getSurrogateServerInfo(const char* wwwName, char* module, int &port);

    /**
     * Get origin server and port for a server.
     * Get a module reference and port number for a specific www name. Used by the suroogate modules
     * when using TCP transport. @see SurrogateServer
     */
    int getOriginServerInfo(const char* wwwName, char* module, int &port);

    /**
     * Get module and port for a random server.
     * Returns a OMNeT++ module name and port number for a randomly chosen server. The general popularity distribution
     * is used in conjunction with the special list to determine the module returned. Called by browser
     * modules to get a random communications partner. @see HttpBrowserBase
     */
    int getRandomSurrogateServerInfo(char* wwwName, char* module, int &port);
    //@}

protected:
    /** Helper used by the server registration to locate the tcpApp getModule(server or browser) */
    cModule* getTcpApp(std::string node);

    /** Set special status of a WWW server. Triggered by an event message. */
    void setSpecialStatus(const char* www, ServerStatus status, double p,
            double amortize);

    /** Cancel special popularity status for a server. Called when popularity has been amortized to zero. */
    void cancelSpecialStatus(const char* www);

    /** Select a server from the special list. This method is called with the pspecial probability. */
    WebServerEntry* selectFromSpecialList();

    /** List the registered servers. Useful for debug. */
    std::string listRegisteredServers();

    /** List the servers on the special list, i.e. those with custom selection probability. Useful for debug. */
    std::string listSpecials();

    /** List the registered servers in the order of the general population pick list. Useful for debug. */
    std::string listPickOrder();

    /**
     * Parse a popularity modification events definition file at startup (if defined).
     * Format: {time};{www name};{event kind};{p value};{amortization factor}
     * Event kind is not used at the present -- use 1 as a default here.
     */
    void parseOptionsFile(std::string file, std::string section);

private:
    /** Get a random server from the special list with p=pspecial or from the general population with p=1-pspecial. */
    WebServerEntry* __getRandomServerInfo();
    /*finds nearest surrogate server according to the given location*/
    WebServerEntry* findNearestWebServerEntry(LocationInfo locationInfo);
};

#endif

