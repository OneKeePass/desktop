This is a local package
All js sources are under 'src' dir

The index.js file in the package root exports all the required functions and components from this local package

### One time

1.  Install this local package's dev dependencies

    yarn install 

2.  Compile all sources under ./src dir using babel ( need to do this for JSX to js )

     ./node_modules/.bin/babel src -d dist  --verbose

3. Add the local package to main project's package.json 
   
   cd .. 
   
   yarn add file:./src-js  (This will add package 'onekeepass-local' to the package.json in ./desktop/)


### Using the exported components from local package

First we need to include the local package in 
'./desktop/src/main/onekeepass/frontend/mui_components.cljs'

```
(ns
 onekeepass.frontend.mui-components
  (:require-macros [onekeepass.frontend.mui-comp-classes
                    :refer  [declare-mui-classes]])
  ....
   ["onekeepass-local" :as okp-local]
  ....
)

(def example-comp
  "A reagent component formed from react componet AutoSizer"
  (reagent.core/adapt-react-class (.-CustomizedBadges ^js/CustomizedBadges okp-local)))

```

### Updating during dev

If we update any js files under ./src, then we need to do the following to reflect and use those changes 

1.  Update 'index.js' if required

2. Compile 
 
   ./node_modules/.bin/babel src -d dist  --verbose

3. Remove and reinstall
   
   cd ..
   
   yarn remove onekeepass-local

   yarn add file:./src-js

4. Then restart REPL and restart tauri frontend to see the new changes



### Babel related

See babel.config.json for the '@babel/preset-react' inclusion as it is required for JSX to js transformations

Also need to add the "ignore": ["node_modules"]; Otherwise sources under './node_modules' also considered by babel and will result in errors

As figwheel uses webpack in the final build, we need not use webpack along with babel here 

