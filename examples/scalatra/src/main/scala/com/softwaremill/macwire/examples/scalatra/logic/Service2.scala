package com.softwaremill.macwire.examples.scalatra.logic

class Service2(submittedData: SubmittedData,
               service3: Service3,
               loggedInUser: LoggedInUser) {
   def fetchStatus = {
     Thread.sleep(20L)
     s"This is Service2. " +
       s"Logged in user: ${loggedInUser.username}, " +
       s"submitted data: ${submittedData.data}, " +
       s"service 3: ${service3.fetchStatus}"
   }
 }
