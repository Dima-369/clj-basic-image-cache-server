# clj-basic-image-server

[![Build Status](https://travis-ci.org/Gira-X/clj-basic-image-cache-server.svg?branch=master)](https://travis-ci.org/Gira-X/clj-basic-image-cache-server)

Very simple image server which takes base64 encoded image URLs, downloads them and provides them to the clients once the download is finished.

## Usage

Make sure that ImageMagick is installed (for Ubuntu `sudo apt install imagemagick`) because the downloaded images are converted to a 80% quality JPG and are downsized to 800 pixels.
Then launch the server, it starts by default on port 8080:

`http://localhost:8080/get/aHR0cHM6Ly9jbG9qdXJlLm9yZy9pbWFnZXMvY2xvanVyZS1sb2dvLTEyMGIucG5n` first downloads the image from `https://clojure.org/images/clojure-logo-120b.png` and responds with a 307 temporary redirect initially, but once the image is downloaded the cached image will be returned directly from this server.

`http://localhost:8080/random` returns one of the 25 random pictures from `resources/random` which are scraped from [https://unsplash.it/800/800/?random](https://unsplash.it/800/800/?random).
The files are included in the repository if you quickly want to try out the server, but see below how you can scrape the images yourself with the provided namespace.

## Modified http-kit

A fork of [http-kit](https://github.com/http-kit/http-kit) is provided as a git submodule in the `checkouts` directory which does not send the `Server: http-kit` header.
If you want to install it, run `git submodule update --init` and `lein install` inside the `checkouts` directory.
Otherwise just ignore it if the header does not bother you.

## Notes

* Image transparency is lost, and replaced with a black color, because of the conversion to JPG
* __Bug__ Multiple requests to the same image URL (if it is not downloaded yet) might result in multiple downloads if the requests are made in quick succession because of the async approach (See [#2](https://github.com/Gira-X/clj-basic-image-cache-server/issues/2))

## Unsplash.it scraper

You can run the included scraper from `src/scraper/scrape_unsplash.clj`.
It overwrites the image files in the `resources/random/` directory.

Run it like this from `lein repl`:

```
(use 'scraper.scrape-unsplash)
(scrape-unsplash {:amount 25 :resolution 800})
```

The scraper namespace is not included in the uberjar.
And note that you should adjust the `random-picture-amount` var in `src/image_server/randomimages.clj` if you have a different amount than the default of 25.
