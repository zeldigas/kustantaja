# User guide

This page will guide you through initial configuration and usage of text2confl. As with many other tools you need to
configure it once and then use it on a regular basis. So this guide is split into 2 parts as well.

[TOC]

## Installation

If you want to run text2confl from your machine, you can download latest version
from [GitHub](https://github.com/zeldigas/text2confl/releases). Downloaded archive contains `text2confl` executable
shell file that can be used to run cli (it's symlink friendly, so you can store distro in any place and create link
in `/usr/local/bin`-like directory).

As an alternative you can use official docker image: `zeldigas/text2confl`. Every release creates a tag with same
version (e.g. `zeldigas/text2confl:0.3.0`) and `latest` always point to last successful build from `master`. This option
is easier for building various automation scenarios and CI/CD pipelines for your documentation.

## Initial setup

Assuming that you plan to store many documents uploaded to confluence and version them in some VCS (e.g. git) it makes
sense to spend some time and configure the place for our docs and make it in the way so that subsequent uploads will
require minimal amount of parameters or not parameters at all. If you want just to quickly upload something, say just
one document you can check [adhoc upload section](#adhoc-upload) below.

### Configuring documentation root

Pick a directory where your documentation will be stored. We will create configuration file to store various defaults in
it and will put *root* pages in it.

#### Configuration file

Configuration file is `.text2confl.yml` (or `.text2conf.yaml` if you prefer this extension more) - create it in
documentation root dir.

For the start put the following contents:

```yaml
server: <address of confluence server>
space: <space for docs>
docs-location: <root url of your documents>
```

Where:

1. `address of confluence server` - is the address of server/datacenter confluence server
   or `https://<YOURNAME>.atlassian.net/wiki` for Cloud version
2. `space for docs` - is space key where you will publish your pages (the string that you can find in url e.g. with
   url https://text2conf.atlassian.net/wiki/spaces/DOCS/pages/2818199/Markdown `DOCS` will be a space we are looking
   for)
3. `root url of your documents` - if address where you source pages can be browsed online. For GitHub, this can be
   something like https://github.com/zeldigas/text2confl/tree/master/docs/ if you want to point to `master` branch and
   your documentation root is in `docs` directory

If all pages will be stored under some parent rather than under SPACE home page, you can also
add `default-parent: Autogenerated docs` (where *Autogenerated docs* is the name of parent page). Note that this parent
will only be used by default and can be overridden on individual pages.

For full list of possible configuration options consult with [configuration reference](configuration-reference.md)

#### Placing your documents

So now you are ready to start working on your documents. `tesxt2confl` process document tree using the following
approach:

1. All files in documents root with supported formats and don't have name starting with `_` are found - these are ***
   root documents***.
2. For every found document, we check if directory with same name and without extension exists. If it is found search is
   repeated there with corresponding root documents considered as parent for documents inside this folder
3. All found files represent tree of documents that is then converted and uploaded. Documents can refer to each other if
   references are supported by used format.

In the following example, `other/important.md` will be skipped, because `other.md` file is missing,
while `storage-formats/markdown.md` as well as `storage-formats/markdown/tips-and-tricks.md` are part of found documents
because corresponding documents are present:

```
.
├── configuration-reference.md
├── contribution.md
├── other
│   └── important.md
├── storage-formats
│   ├── markdown
│   │   └── tips-and-tricks.md
│   ├── markdown.md
│   └── markdown.png
├── storage-formats.md
├── .text2confl.yml
└── user-guide.md
```

If you don't have single root where all documents are stored under, you can configure corresponding parents in
documents. It is format dependent and can be found in pages about [storage formats](storage-formats.md).

#### Page attachments

If you referer any file that is not part of document tree, but it exists, it is considered as page attachment that will
be uploaded to page. In example above, `markdown.png` file is referred from `markdown.md` document, so it will be
attached to this page.

### Authentication parameters

So you almost ready to upload your documents, but to do it you need to get credentials to access Confluence. It depends
on the Confluence type you use - cloud or server/data center - how to acquire them and how to pass.

`text2confl` supports 2 ways of authentication:

1. api token *without* username provided as `--access-token` ,
2. combination of username and password provided as `--user` and `--password` parameters. If username is provided, but
   password is omitted, it will be prompted in interactive way.

#### Getting credentials

For **Cloud,** you need to get security api token but use it as a password in conjunction with your email. You can
check [official docs](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/)
for details on how to generate api token.

For **Server/DataCenter** you have 2 options:

1. Plain username and password
2. Personal access token -
   check [official docs](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) to
   find the way to generate it.

#### Additional ways to provide credentials

Instead of providing creds as command line args you can specify them as env variables (handy for various automation
scripts) or store them in special configuration file that is looked up in the following locations:

1. `$XDG_CONFIG_HOME/text2confl/config.properties` (or `~/.config/text2confl/config.properties` if `XDG_CONFIG_HOME` env
   var is not set)
2. `text2confl.properties` file in current directory (the one that you )

file can be convenient if you upload docs from you machine on regular basis.

| cli argument     | env name                  | property name in configuration file |
|------------------|---------------------------|-------------------------------------|
| `--access-token` | `CONFLUENCE_ACCESS_TOKEN` | `upload.access-token`               |
| `--user`         | `CONFLUENCE_USER`         | `upload.user`                       |
| `--password`     | `CONFLUENCE_PASSWORD`     | `upload.password`                   |

## Working with configured docs

After you configured directory you can upload contents as simple as:

```shell
text2confl upload --docs .
```

Assuming that you are currently in directory with docs. You can also enable ***dry** mode for upload by passing `--dry`
option to command. In dry-run mode no confluence content will be modified, instead all changes will be logged
with `(dryrun)` marker.

If you want to see how your documents will be converted in confluence *storage format* without uploading them (or if you
just want conversion part because for some reason you plan to upload them yourself) you can use `convert` command to
produce documents as files on machine:

```shell
text2confl convert --docs .
```

### Using docker image

If you prefer to use docker image instead of downloading archive with release, then command execution will look like:

```shell
# assuming that we access confluence with personal access token
docker run --rm -it -v $PWD:/wd --workdir /wd \
  \ -e CONFLUENCE_ACCESS_TOKEN=....
  zeldigas/text2confl upload --docs .
```

If you store credentials in `properties` file that is in your config directory, it can be passed inside in this way:

```shell
docker run --rm -it -v ~/.config/text2confl:/root/.config/text2confl:ro -v $PWD:/wd --workdir /wd zeldigas/text2confl upload --docs .
```

## Adhoc upload

If you just need to upload one file, or you are fine with providing all the options via command line it is possible to skip creation of `.text2confl.yml` file:

```shell
text2confl upload --docs . \
  --confluence-url https://wiki.example.org \
  --user bob \
  --password secret_password \
  --space DOCS \
  --parent "Autogenerated docs"
```

You can upload just one file if you provide path to it to `--docs` option instead of path to directory:

```shell
text2confl upload --docs ./my-file.md \
  --confluence-url https://wiki.example.org \
  --user bob \
  --password secret_password \
  --space DOCS
```