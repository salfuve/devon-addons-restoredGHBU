oasp4j-kieker
=============

An IP module for integrating OASP4J with the [Kieker monitoring framework](http://kieker-monitoring.net/).

This module is based on the [bachelor thesis by Johannes Mensing](docs/Bachelorarbeit.pdf) which is stored in the docs directory. A comprehensive guide (cookbook) for integrating this module into a spring based Java application is given in the [PM-Leitfaden](docs/PM-Leitfaden/PM-Leitfaden.html) (both documents are in german language).

Note that the *pom.xml* and the files given in *src/main/resources/samples* have been adopted to reflect the changes needed for integrating the module with OASP. The above references documentation does not yet reflect these changes.

**This module is still work-in-progress. So don't expect everything to work straughtforward.**

Quick-Start
-----------
The following is a very short introduction how to integrate OAS4J with Kieker. Note that this will enable monitoring with some predefined patterns for which method calls will be monitored. This will only give useful
results for the OASP4J example application. Please refer to the above mendtioned [PM-Leitfaden](docs/PM-Leitfaden/PM-Leitfaden.html) for any further configuration and explanation.

### Changes to be introduced to the OASP4J based application
To integrate Kieker monitoring into an OASP4J based application (e.g. oasp4j-example-application) you need to perform the following steps in the OASP4J based application:

* Integrate a maven dependency to this module into the pom:

    <dependency>
      <groupId>io.oasp.java</groupId>
      <artifactId>oasp4j-kieker</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
* Copy the file *performance-monitoring.xml* from the folder *src/main/resources/samples* to the folder *src/main/resources/config/app* of the OASP4J application.
* Introduce the following line in *src/main/resources/config/app/beans-application.xml* of the OASP4J application for importing the above spring configuration:

    <import resource="performance-monitoring.xml" />
* Copy the file *kieker.monitoring.properties* from the folder *src/main/resources/samples* to the folder *src/main/resources/META-INF* of the OASP4J application.

### Location of raw data
The raw data collected by the kieker framework will be stored to a path given via the property *kieker.monitoring.writer.filesystem.AsyncFsWriter.customStoragePath* in the file *kieker.monitoring.properties*.
Please assure that the directory given there exists and is writable. (Default is *C:\KiekerData*)

### Analysis of data
T.B.D (see Chapter 3 of [PM-Leitfaden](docs/PM-Leitfaden/PM-Leitfaden.html))
