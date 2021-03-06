(ns conjure.core.util.loading-utils
  (:import [java.io File FileInputStream FileNotFoundException])
  (:require [clojure.contrib.classpath :as classpath]
            [clojure.contrib.logging :as logging]
            [clojure.contrib.ns-utils :as ns-utils]
            [clojure.contrib.seq-utils :as seq-utils]
            [clojure.contrib.str-utils :as clojure-str-utils]
            [clojure.contrib.java-utils :as java-utils]
            [clojure.set :as clj-set]
            [conjure.core.util.string-utils :as string-utils]))

(defn
#^{ :doc "Gets the system class loader" }
  system-class-loader []
  (ClassLoader/getSystemClassLoader))

(defn
  user-classpath-var? []
  (resolve 'user/*classpath*))

(defn
  classpath-directories []
  (if-let [classpath (user-classpath-var?)]
    (filter #(.isDirectory %) (map #(File. %) (.split (var-get classpath) (System/getProperty "path.separator"))))
    (classpath/classpath-directories)))

(defn
  find-all-classpath-files [full-file-path]
  (filter #(.exists %) 
    (map #(File. % full-file-path) (classpath-directories))))

(defn
  find-classpath-file [full-file-path]
  (first (find-all-classpath-files full-file-path)))

(defn 
#^{ :doc "Returns a stream for the given resource if it exists. Otherwise, this function returns nil." }
  find-resource [full-file-path]
  (if-let [resource (.getResourceAsStream (system-class-loader) full-file-path)]
    resource
    (when (user-classpath-var?)
      (when-let [file (find-classpath-file full-file-path)]
        (FileInputStream. file)))))

(defn
#^{ :doc "Returns a sequence of streams for the given resource if it exists. Otherwise, this function returns an empty
sequence." }
  find-resources [full-file-path]
  (let [resource-streams (map #(.openStream %) (enumeration-seq (.findResources (system-class-loader) full-file-path)))]
    (if (user-classpath-var?)
      (concat resource-streams (map #(FileInputStream. %) (find-all-classpath-files full-file-path)))
      resource-streams)))

(defn
 #^{ :doc "Returns true if the given resource exists. False otherwise." }
  resource-exists? [full-file-path]
  (let [resources (enumeration-seq (.findResources (system-class-loader) full-file-path))]
    (if (and resources (not-empty resources))
      true
      (if (user-classpath-var?)
        (let [file-resources (find-all-classpath-files full-file-path)]
          (and file-resources (not-empty file-resources)))
        false))))

(defn
#^{ :doc "Loads a given director and filename using the system class loader and returns the reader for it." }
  resource-reader [directory filename]
  (let [full-file-path (str directory "/" filename)
        resource (find-resource full-file-path)]
    (if resource
      (new java.io.InputStreamReader resource)
      (throw (new RuntimeException (str "Cannot find file named: " full-file-path))))))

(defn
#^{:doc "Loads a resource from the class path. Simply pass in the directory and the filename to load."}
  load-resource [directory filename]
  (let [reader (resource-reader directory filename)]
    (try
      (load-reader reader)
      (catch Exception exception
        (throw (RuntimeException. (str "An error occured while reading file: " directory "/" filename) exception)))
      (finally (. reader close)))))

(defn 
#^{:doc "Loads a resource into a string and returns it."}
  load-resource-as-string [directory filename]
  (let [reader (resource-reader directory filename)
        output (new StringBuffer)]
    (loop [current-char (. reader read)]
      (if (== current-char -1)
        (. output toString)
        (do
          (. output append (char current-char))
          (recur (. reader read)))))))

(defn
#^{:doc "Converts the given input stream into a lazy sequence of bytes."}
  seq-input-stream
  ([input-stream] (map byte (take-while #(>= % 0) (repeatedly #(. input-stream read)))))
  ([input-stream length] (map byte (take length (repeatedly #(. input-stream read))))))

(defn
#^{:doc "Converts an input stream to a byte array."}
  byte-array-input-stream 
  ([input-stream] (into-array Byte/TYPE (seq-input-stream input-stream)))
  ([input-stream length] (into-array Byte/TYPE (seq-input-stream input-stream length))))

(defn
#^{:doc "Converts an input stream to a string."}
  string-input-stream 
  ([input-stream] (new String (byte-array-input-stream input-stream)))
  ([input-stream length] (new String (byte-array-input-stream input-stream length))))

(defn 
#^{:doc "Gets the dir from the class path which ends with the given ending"}
  get-classpath-dir-ending-with [ending]
  (seq-utils/find-first 
      (fn [directory]
        (.endsWith (.getPath directory) ending))
      (classpath/classpath-directories)))
    
(defn
#^{:doc "Converts all dashes to underscores in string."}
  dashes-to-underscores [string]
  (if string
    (clojure-str-utils/re-gsub #"-" "_" string)
    string))
    
(defn
#^{:doc "Converts all underscores to dashes in string."}
  underscores-to-dashes [string]
  (if string
    (clojure-str-utils/re-gsub #"_" "-" string)
    string))
  
(defn
#^{:doc "Returns the file separator used on this system."}
  file-separator []
  (java-utils/get-system-property "file.separator"))
  
(defn
#^{:doc "Converts all slashes to periods in string."}
  slashes-to-dots [string]
  (if string
    (clojure-str-utils/re-gsub #"/|\\" ; "\" Fixing a bug with syntax highlighting
       "." string) 
    string))
    
(defn
#^{ :doc "Converts all periods to slashes in string." }
  dots-to-slashes [string]
  (if string
    (. string replace "." (file-separator))
    string))

(defn
#^{ :doc "Returns true if the given file is a clojure file." }
  clj-file? [file]
  (and (.isFile file) (.endsWith (.getName file) ".clj")))

(defn
#^{:doc "Converts the given clj file name to a symbol string. For example: \"loading_utils.clj\" would get converted into \"loading-utils\""}
  clj-file-to-symbol-string [file-name]
  (slashes-to-dots (underscores-to-dashes (string-utils/strip-ending file-name ".clj"))))

(defn
#^{:doc "Converts the given symbol string to a clj file name. For example: \"loading-utils\" would get converted into \"loading_utils.clj\""}
  symbol-string-to-clj-file [symbol-name]
  (let [dashed-name (dashes-to-underscores symbol-name)]
    (if (and dashed-name (> (. dashed-name length) 0))
      (str (dots-to-slashes dashed-name) ".clj")
      dashed-name)))

(defn
#^{ :doc "Returns the namespace of the given file assuming the classpath include the given classpath parent 
directory." }
  file-namespace [classpath-parent-directory file]
  (if file
    (string-utils/strip-ending
      (clojure-str-utils/str-join "." 
        (map underscores-to-dashes 
          (string-utils/tokenize 
            (if classpath-parent-directory
              (.substring (.getPath file)
                (.length (.getPath classpath-parent-directory))) 
              (.getPath file))
            "\\/")))
      ".clj")))

(defn
#^{ :doc "Returns a string for the namespace of the given file in the given directory." }
  namespace-string-for-file [directory file-name]
  (if file-name
    (if (and directory (> (. (. directory trim) length) 0))
      (let [trimmed-directory (. directory trim)
            slash-trimmed-directory (if (or (. trimmed-directory startsWith "/") 
                                            (. trimmed-directory startsWith "\\")) ;" Fix highlight issue.
                                            (. trimmed-directory substring 1) trimmed-directory)]
        (str (slashes-to-dots (underscores-to-dashes slash-trimmed-directory)) "." (clj-file-to-symbol-string file-name)))
      (clj-file-to-symbol-string file-name))
    file-name))

(defn
#^{ :doc "Returns true if the given var-name is in a conjure namespace (controller, helper, model or view)." }
  conjure-namespace? [var-name]
  (or 
    (.startsWith var-name "controllers.")
    (.startsWith var-name "helpers.")
    (.startsWith var-name "models.")
    (.startsWith var-name "views.")))

(defn
#^{ :doc "Returns a set of conjure namespaces (controllers, models, helpers and views) used by the given controller." }
  conjure-namespaces [namespace-name]
  (let [namespace-to-search (ns-utils/get-ns (symbol namespace-name))]
    (reduce
      (fn [namespace-set var-name] 
        (conj namespace-set 
          (let [slash-index (.indexOf var-name "/")]
            (if (> slash-index 0) 
              (.substring var-name 0 slash-index)
              var-name))))
      #{}
      (filter 
        conjure-namespace?
        (concat 
          (map str (vals (ns-aliases namespace-to-search)))
          (map #(.substring (str %) 2) 
            (filter #(not (.startsWith (str %) "#'clojure"))
              (vals (ns-refers namespace-to-search)))))))))

(defn
#^{ :doc "Reloads all of the given namespaces." }
  reload-namespaces [namespaces]
  (doseq [ns-to-load namespaces]
    (require :reload (symbol ns-to-load))))

(defn
#^{ :doc "Reloads all of the conjure namespaces refered to by the namespace with the given name." }
  reload-conjure-namespaces
  ([namespace-name] (reload-conjure-namespaces namespace-name #{}))
  ([namespace-name loaded-namespaces]
    (let [namespaces-to-load (filter #(not (contains? loaded-namespaces %)) (conjure-namespaces namespace-name))]
      (when (not-empty namespaces-to-load)
        (reload-namespaces namespaces-to-load)
        (doseq [child-namespace namespaces-to-load]
          (reload-conjure-namespaces child-namespace (clj-set/union loaded-namespaces (set namespaces-to-load))))))))
          
(defn
#^{ :doc "Returns the value of the given var symbol in the given namespace or default if the var or the namespace
cannot be found.." }
  resolve-ns-var [ns-sym var-sym default]
  (let [ns-found (find-ns 'ns-sym)]
    (if ns-found
      (or (ns-resolve ns-found var-sym) default)
      default)))

(defn
#^{ :doc "Reloads all of the given namespaces." }
  namespace-exists? [namespace]
  (if (symbol? namespace)
    (try
      (require (symbol namespace))
      true
      (catch FileNotFoundException fileNotFound
        false))
    (namespace-exists? (symbol (str namespace)))))

(defn
#^{ :doc "Returns true if the given zip entry is in the given directory." }
  entry-in-directory? [zip-entry dir-name]
  (when (and zip-entry dir-name)
    (let [entry-name (.getName zip-entry)]
      (and (not (= entry-name dir-name)) (.startsWith entry-name dir-name)))))

(defn
#^{ :doc "Returns all of the zip entries in the classpath with the given directory name." }
  directory-zip-entries [jar-file dir-name]
  (filter #(entry-in-directory? % dir-name) (enumeration-seq (.entries jar-file))))

(defn
#^{ :doc "Returns all of the zip entries in the classpath with the given directory name." }
  class-path-zip-entries [dir-name]
  (mapcat #(directory-zip-entries % dir-name) (classpath/classpath-jarfiles)))

(defn
#^{ :doc "Returns all of the files in the sub directory of the given parent directory, if the full parent and sub 
directory exists. Otherwise, this function returns nil." }
  files-in-sub-dir [parent-dir sub-dir]
  (let [full-dir (File. parent-dir sub-dir)]
    (when (.exists full-dir)
      (.listFiles full-dir))))

(defn
#^{ :doc "Returns all of the file names in the given directory in all of the class path directories." }
  all-class-path-dir-file-names [dir-name]
  (map #(.getName %)
    (mapcat #(files-in-sub-dir % dir-name) (classpath-directories))))

(defn
#^{ :doc "Returns the direct child directory of parent directory in the given zip-entry." }
  zip-entry-child-dir [zip-entry parent-dir]
  (let [parent-dir-count (if (.endsWith parent-dir "/") (count parent-dir) (inc (count parent-dir)))
        zip-entry-name (.substring (.getName zip-entry) parent-dir-count)
        dir-separator-index (.indexOf zip-entry-name "/")]
    (if (> dir-separator-index 0)
      (.substring zip-entry-name 0 (.indexOf zip-entry-name "/"))
      zip-entry-name)))

(defn
#^{ :doc "Returns all of the file names in the given directory in all of the class path jars." }
  all-class-path-jar-file-names [dir-name]
  (reduce #(conj %1 (zip-entry-child-dir %2 dir-name)) #{} (class-path-zip-entries dir-name)))

(defn
#^{ :doc "Returns all of the file names in the given directory on the class path." }
  all-class-path-file-names [dir-name]
  (concat (all-class-path-dir-file-names dir-name) (all-class-path-jar-file-names dir-name)))