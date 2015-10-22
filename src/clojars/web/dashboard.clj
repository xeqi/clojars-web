(ns clojars.web.dashboard
  (:require [clojars.web.common :refer [html-doc html-doc-with-large-header jar-link group-link tag]]
            [clojars.db :refer [jars-by-username find-groupnames recent-jars]]
            [clojars.stats :as stats]
            [hiccup.element :refer [unordered-list link-to]]))

(defn recent-jar [jar-map]
  (let [stats (stats/all)
        description (:description jar-map)
        truncate-length 120]
    [:li.col-md-4.col-sm-6.col-xs-12.col-lg-4
     [:div.recent-jar
      [:h3.recent-jar-title
       (jar-link jar-map)]
      [:p.recent-jar-description
       (if (> (count description) truncate-length)
         (str (subs description 0 truncate-length) "...")
         description)]
      [:p.hint.total-downloads "Downloads: " (stats/download-count stats
                                                                   (:group_name jar-map)
                                                                   (:jar_name jar-map))]]]))

(defn index-page [db account]
  (html-doc-with-large-header account nil
    [:article.row
     [:div.push-information.col-md-6.col-lg-6.col-sm-6.col-xs-12
      [:h3.push-header "Push with Leiningen"]
      [:div.push-example
       [:pre.push-example-leiningen
        (tag "$") " lein deploy clojars\n"]]]
     [:div.push-information.col-md-6.col-lg-6.col-sm-6.col-xs-12
      [:h3.push-header "Maven Repository"]
      [:div.push-example
       [:pre
        (tag "<repository>\n")
        (tag "  <id>") "clojars.org" (tag "</id>\n")
        (tag "  <url>") "http://clojars.org/repo" (tag "</url>\n")
        (tag "</repository>")]]]]
    [:div.recent-jars-header-container.row
     [:h2.recent-jars-header.col-md-12.col-lg-12.col-sm-12.col-xs-12
      "Recently pushed projects"]]
    [:ul.recent-jars-list.row (map recent-jar (recent-jars db))]))

(defn dashboard [db account]
  (html-doc account "Dashboard"
    [:div.light-article.col-md-12.col-lg-12.col-xs-12.col-sm-12
     [:h1 (str "Dashboard (" account ")")]
     [:div.col-md-4.col-lg-4.col-sm-4.col-xs-12
      [:div.dash-palette
       [:h2 "Your Projects"]
       (let [jars (jars-by-username db account)]
         (if (seq jars)
           (unordered-list (map jar-link jars))
           [:p "You don't have any projects, would you like to "
            (link-to "http://wiki.github.com/ato/clojars-web/pushing" "add one")
            "?"]))]]
     [:div.col-md-4.col-lg-4.col-sm-4.col-xs-12
      [:div.dash-palette
       [:h2 "Your Groups"]
       (unordered-list (map group-link (find-groupnames db account)))]]
     [:div.col-md-4.col-lg-4.col-sm-4.col-xs-12
      [:div.dash-palette
       [:h2 "FAQ"]
       [:ul
        [:li (link-to "https://github.com/ato/clojars-web/wiki/Tutorial" "How I create a new project?")]
        [:li (link-to "http://wiki.github.com/ato/clojars-web/pushing" "How do I deploy to clojars?")]
        [:li (link-to "https://github.com/ato/clojars-web/wiki/Data" "How can I access clojars data programatically?")]
        [:li (link-to "https://github.com/ato/clojars-web/wiki/Groups" "What are groups?")]
        [:li (link-to "https://github.com/ato/clojars-web/wiki/POM" "What does my POM need to look like?")]]]]]))
