(ns puppetlabs.trapperkeeper.services.webserver.normalized-uri-helpers-test
  (:import (javax.servlet.http HttpServletRequest))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.normalized-uri-helpers
             :as normalized-uri-helpers]))

(defn normalize-uri-path-for-string
  [uri]
  (normalized-uri-helpers/normalize-uri-path
   (reify HttpServletRequest
     (getRequestURI [_] uri))))

(deftest normalize-uris-with-no-special-characters-tests
  (testing "uris with no special characters are preserved after normalization"
    (is (= "" (normalize-uri-path-for-string "")))
    (is (= "/" (normalize-uri-path-for-string "/")))
    (is (= "/foo" (normalize-uri-path-for-string "/foo")))
    (is (= "/foo/bar" (normalize-uri-path-for-string "/foo/bar")))))

(deftest normalize-uris-with-params-in-path-segments-tests
  (testing (str "parameters in uri path segments are preserved after "
                "normalization")
    (is (= "/foo;foo=chump" (normalize-uri-path-for-string "/foo;foo=chump")))
    (is (= "/foo/bar;bar=chocolate/baz"
           (normalize-uri-path-for-string "/foo/bar;bar=chocolate/baz")))))

(deftest normalize-uris-with-percent-encoded-characters-tests
  (testing (str "percent-encoded characters are properly decoded after "
                "normalization")
    (is (= "/foo" (normalize-uri-path-for-string "/%66oo")))
    (is (= "/foo/b a r" (normalize-uri-path-for-string
                         "/foo%2f%62%20a%20%72"))))
  (testing "malformed percent-encoded character throws exception"
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%b%a%r")))))

(deftest normalize-uris-with-relative-paths-tests
  (testing "non percent-encoded relative paths are normalized as an error"
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string ".")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string "..")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string "/.")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/..")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/.")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/..")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/./bar")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/../bar"))))
  (testing "percent-encoded relative paths are normalized as an error"
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "%2E")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "%2E%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/%2E%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E/bar") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E%2E/bar") )))
  (testing (str "period characters not representing relative paths are not "
                "normalized as an error")
    (is (= "/foo/.bar" (normalize-uri-path-for-string "/foo/.bar")))
    (is (= "/foo/..bar" (normalize-uri-path-for-string "/foo/..bar")))
    (is (= "/foo/bar." (normalize-uri-path-for-string "/foo/bar.")))
    (is (= "/foo/bar.." (normalize-uri-path-for-string "/foo/bar..")))
    (is (= "/foo/bar.../baz" (normalize-uri-path-for-string "/foo/bar.../baz")))
    (is (= "/foo/.bar" (normalize-uri-path-for-string "/foo/%2Ebar")))
    (is (= "/foo/..bar" (normalize-uri-path-for-string "/foo/%2E%2Ebar")))
    (is (= "/foo/bar." (normalize-uri-path-for-string "/foo/bar%2E")))
    (is (= "/foo/bar.." (normalize-uri-path-for-string "/foo/bar%2E%2E")))
    (is (= "/foo/bar/.../baz" (normalize-uri-path-for-string
                               "/foo/bar/%2E%2E%2E/baz")))))

(deftest normalize-uris-with-redundant-slashes-tests
  (testing "uris with redundant slashes are removed"
    (is (= "/" (normalize-uri-path-for-string "//")))
    (is (= "/foo" (normalize-uri-path-for-string "//foo")))
    (is (= "/foo" (normalize-uri-path-for-string "///foo")))
    (is (= "/foo/bar" (normalize-uri-path-for-string "/foo//bar")))
    (is (= "/foo/bar/" (normalize-uri-path-for-string "/foo//bar///")))))

(deftest normalize-uris-with-percent-decoding-and-slash-removal
  (testing (str "uris with both percent-encoded characters and redundant "
                "slashes are properly normalized")
    (is (= "/foo/b a r" (normalize-uri-path-for-string
                         "/%66oo%2f%2f%2f%62%20a r")))))
