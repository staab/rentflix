# The Rentflix Project

The Rentflix Project is an effort to provide a web interface exposing the media available at old-school specialty video rental shops.

# Installation

Install [Leiningen](https://github.com/technomancy/leiningen)

```
git clone git@github.com:staab/rentflix.git
cd rentflix
lein deps
lein git-deps
```

Install Datomic - follow the directions [here](http://docs.datomic.com/getting-started.html). You can either create your own datomic account and use your own credentials, or you can use mine. In either case, you should end up with a file called `!/.lein/credentials.clj`. You'll need to encrypt it. To do this, run `gpg --gen-key` and follow the steps. Then, run `gpg --default-recipient-self -e ~/.lein/credentials.clj > ~/.lein/credentials.clj.gpg`.

# Implementation

The primary reason we chose the tech stack outlined below is for our own personal development. This project is built on technologies we haven't used, and in which we want some substantial experience.

# Backend

We'll be using Clojure with [Ring](https://github.com/ring-clojure/ring) for our http server with [Compojure](https://github.com/weavejester/compojure) for routing. We'll use [Datomic](http://docs.datomic.com/) for our database. 

The backend will expose a [GraphQL](https://facebook.github.io/react/blog/2015/05/01/graphql-introduction.html) API for use by a web frontend.

# Frontend

We'll use Clojurescript with [Om](https://github.com/omcljs/om) [React](https://facebook.github.io/react/), and maybe [Flux](https://facebook.github.io/flux/) if we need it.

# Running

```
lein ring server-headless
```

When you make changes, they should automatically be applied on the next page reload, unless the server crashed.

You can also run a repl with `lein repl`. You can load and reload namespaces with e.g. `(use 'rentflix.server :reload)`.

# Learning

## Compojure

- [SO explanation of defroutes](http://stackoverflow.com/a/3490479/1467342)

## Datomic

- [API reference](http://docs.datomic.com/clojure/index.html#datomic.api/delete-database)
- [Tutorial](http://docs.datomic.com/tutorial.html#making-a-database)
- [Day of Datomic](https://github.com/Datomic/day-of-datomic)

# Domain

http://docs.themoviedb.apiary.io/#reference/movies/movieidsimilar/get

Search on:
    - Title
    - Year
    - Genres
    - Tagline
    - Overview
    - Keywords

Filter on:
    - Adult
    - Year
    - Genres

Get by TMDB endpoint:
    - Popular
    - Top Rated
    - Similar to other