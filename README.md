![PhoenixNAP Logo](https://phoenixnap.com/wp-content/themes/phoenixnap-v2/img/v2/logo.svg)

[![Build Status](https://travis-ci.org/phoenixnap/springmvc-raml-plugin.svg?branch=develop)](https://travis-ci.org/phoenixnap/springmvc-raml-plugin)

## Spring MVC-RAML Synchronizer
The Spring MVC-RAML Sychronizer parent project aims to provide a live representation of the endpoints exposed in a project by using the Spring MVC Annotation in that project as the single source of truth. 
This can be used to either:

1. [Generate RAML code from Spring annotations](https://github.com/phoenixnap/springmvc-raml-plugin/tree/master/springmvc-raml-plugin#usage-1---generating-raml-from-implementation)
2. [Keep hand-written RAML files in sync with the Spring MVC implementation by cross-checking the contract with the implementation](
https://github.com/phoenixnap/springmvc-raml-plugin/tree/master/springmvc-raml-plugin#usage-2---style-checking-raml-document-and-verifying-against-implementation)
3. [Generate SpringMVC Rest Controller Endpoints from a RAML file]( https://github.com/phoenixnap/springmvc-raml-plugin/tree/master/springmvc-raml-plugin#usage-3---generating-springmvc-server-endpoints-from-a-raml-file)

When generating RAML, the project will extract information using reflection and source inspection (for JavaDoc) so as to expose all information available to it.

The project provides three artifacts:
- springmvc-raml-plugin: A maven plugin designed to be run on Java 8 code which has been compiled with argument name information. 
- springmvc-raml-parser: This is a seperate project that contains the parser which converts Spring MVC annotations to a RAML Model
- springmvc-raml-annotations: This project allows the use of custom annotations such as @Example which can be used to embed example inputs or outputs.


## Documentation & Getting Support
Usage and documentation are available in the Javadoc and README.md of the child projects. Kindly contact the developers via email (available in the pom files) if required or open an [Issue][] in our tracking system.

## Building from Source
The SpringMVC-RAML plugin uses a [Maven][]-based build system.

## Prerequisites
[Git][] and [JDK 8 update 20 or later][JDK8 build]

Be sure that your `JAVA_HOME` environment variable points to the `jdk1.8.0` folder
extracted from the JDK download.

## License
The SpringMVC-RAML plugin is released under version 2.0 of the [Apache License][].

## Contributing
[Pull requests][] are welcome; Be a good citizen and create unit tests for any bugs squished or features added

[Pull requests]: http://help.github.com/send-pull-requests
[Apache License]: http://www.apache.org/licenses/LICENSE-2.0
[Git]: http://help.github.com/set-up-git-redirect
[JDK8 build]: http://www.oracle.com/technetwork/java/javase/downloads
[Maven]: http://maven.apache.org/
[Issue]: https://github.com/phoenixnap/springmvc-raml-plugin/issues
