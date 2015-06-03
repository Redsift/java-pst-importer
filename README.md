# java-pst-importer

Imports a PST file and either:
--
* adds messages as JMAP into a Beanspike job queue
* writes messages as JMAP into a file

Build instructions:
--
* Needs the <code>java-libjpst</code> project to be built and installed in your machine
* <code>mvn clean install</code> will build it
	
Running instructions:
--
* <code>java -jar target/java-pst-importer-0.1.2-jar-with-dependencies.jar -help</code> will display all available options
