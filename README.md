# clj-basic-image-server

[![Build Status](https://travis-ci.org/Gira-X/clj-basic-image-cache-server.svg?branch=master)](https://travis-ci.org/Gira-X/clj-basic-image-cache-server)

Very simple image server which takes base64 encoded image URLs, downloads them and provides them to the clients once the download is finished.

## Usage

Make sure that ImageMagick is installed (for Ubuntu `sudo apt install imagemagick`) because the downloaded images are converted to a 80% quality JPG and are downsized to 800 pixels.
Then launch the server, it starts by default on port 8080:

`http://localhost:8080/random` returns one of the 48 random pictures from `resources/random`which are scraped from `https://unsplash.it/600/600/?random`.

`http://localhost:8080/get/aHR0cHM6Ly9jbG9qdXJlLm9yZy9pbWFnZXMvY2xvanVyZS1sb2dvLTEyMGIucG5n` first downloads the image from `https://clojure.org/images/clojure-logo-120b.png` and responds with a 307 temporary redirect initially, but once the image is downloaded the cached image will be returned directly from this server.

## Modified http-kit

A fork of [http-kit](https://github.com/http-kit/http-kit) is provided in the `checkouts` directory which does not send the `Server: http-kit` header.
If you want to install it, run `git submodule update --init` and `lein install` inside the `checkouts` directory.
Otherwise just ignore it if the header does not bother you.

## Notes

* Image transparency is lost, and replaced with a black color, because of the conversion to JPG
