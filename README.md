youtrack-plugin
===============

# License #
youtrack-plugin is released under the MIT License. See the bundled LICENSE file for details.

# About #
This is a plugin for Jenkins_ that aims for providing support for [YouTrack](http://www.jetbrains.com/youtrack) inside [Jenkins](http://jenkins-ci.org).

# Requirements #

This plugin requires the [jquery plugin](https://wiki.jenkins-ci.org/display/JENKINS/jQuery+Plugin) to be installed

# Usage #

YouTrack sites can be set up under global configuration. For each job a site can be selected, and specific options can be
set.

There is also a build step to update the a YouTrack Build bundle with the build.

# Build #
To build from command line, install Maven (see http://www.mkyong.com/maven/how-to-install-maven-in-windows/), open a prompt to the youtrack-plugin directory, and run "mvn clean install".

To build from IntelliJ, start IntelliJ and choose "open project".  Select pom.xml from the youtrack-plugin directory.  Once the project is open, choose menu item View –> Tool Windows –> Maven Projects.  From the Maven Projects window, choose the play button (green arrow) to run Maven build, which should succeed.  If not, right click the youtrack-plugin node and try reimporting dependencies.

