Rundeck Plugin for Graylog
==========================

[![Build Status](https://travis-ci.org/Graylog2/graylog-plugin-rundeck.svg)](https://travis-ci.org/Graylog2/graylog-plugin-rundeck)

An alarm callback plugin for integrating [Rundeck](http://rundeck.org) into [Graylog](https://www.graylog.org).

**Required Graylog version:** 1.0 and later

## Installation

[Download the plugin](https://github.com/Graylog2/graylog-plugin-rundeck/releases)
and place the `.jar` file in your Graylog plugin directory. The plugin directory
is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

## Usage

You should now be able to add Rundeck callbacks to your stream alert configurations. In order to establish a connection to the
Rundeck API request an API token from the admin section of Rundeck.

![Screenshot: Adding a Rundeck alarm callback](https://s3.amazonaws.com/graylog2public/images/plugin-rundeck-connect.png)

The API url should look like `http://172.16.10.1:4440`. The job ID can be found in the Rundeck job definition under `UUID`.

### Node filter

If the job is not bound to a set of nodes in Rundeck you can define node filters in the plugin

![Screenshot: Creating node filters](https://s3.amazonaws.com/graylog2public/images/plugin-rundeck-filter.png)

It is possible to define filters by node `name`, `hostname`, `tag` and a bunch of operating system properties like `os-[name, family, arch, version]`.
Filters are separated into include and exclude filters. In this way it is possible to select a big group of nodes first and then specify the actual nodes
inside this group. For example you can use an include filter like `tags:database` to select all database nodes and then become more precise and exclude all
master nodes with an exclude filter `tags:master`. The result is a list of slave database nodes.
By default the exclude filter have precidence over the include filters. Use the checkbox at the bottom to invert the result.
Rundeck filters are not completely intuative you can read more [here](http://rundeck.org/2.4.2/api/index.html#using-node-filters).

### Job arguments

![Screenshot: Set job arguments](https://s3.amazonaws.com/graylog2public/images/plugin-rundeck-args.png)

To parametrize a job you can set static job arguments. These parameters can be set by the user once and they are the same for every job execution.
Alternatively is is possible to extract fields from the last log message of your stream alert and add these fields as job parameters.
E.g. to get the source address of the alarming database server you can add `source` to the field list. This is the same as executing a shell command
with a command line parameter `job.sh -source 172.16.10.10`. In this way it is possible to react dynamically on events and informations from you log data.

Rundeck executes now jobs when the stream condition is triggered.

## Build

This project is using Maven and requires Java 7 or higher.

You can build a plugin (JAR) with `mvn package`.

DEB and RPM packages can be build with `mvn jdeb:jdeb` and `mvn rpm:rpm` respectively.

## Plugin Release

We are using the maven release plugin:

```
$ mvn release:prepare
[...]
$ mvn release:perform
```

This sets the version numbers, creates a tag and pushes to GitHub. TravisCI will build the release artifacts and upload to GitHub automatically.
