MONGODB HORIZONTAL DATABASE SHARDING
STEP-BY-STEP IMPLEMENTATION GUIDE

============================================================
1. OVERALL ARCHITECTURE
============================================================

Windows PC
    |
    +-- Docker Network
          |
          +-- Config Server (configsvr:27017)
          |
          +-- mongos (Query Router)
          |
          +-- Shard 1 (shard01:27017)
          |
          +-- Shard 2 (shard02:27017)

Final Student collection:

College.Student
Shard Key: RollNo

RollNo < 6  --> shard1ReplSet
RollNo >= 6 --> shard2ReplSet


============================================================
2. CREATE PROJECT FOLDER
============================================================

Create:

C:\mongodb-sharding

Open Windows Command Prompt:

cd C:\mongodb-sharding


============================================================
3. CREATE docker-compose.yml
============================================================

Create a file named:

docker-compose.yml

Put the following code inside it:

------------------------------------------------------------

services:

  configsvr:
    image: mongo:latest
    container_name: configsvr
    command: mongod --configsvr --replSet configReplSet --port 27017 --bind_ip_all
    ports:
      - "27019:27017"
    volumes:
      - config_data:/data/db
    networks:
      - mongo_cluster

  shard01:
    image: mongo:latest
    container_name: shard01
    command: mongod --shardsvr --replSet shard1ReplSet --port 27017 --bind_ip_all
    ports:
      - "27018:27017"
    volumes:
      - shard01_data:/data/db
    networks:
      - mongo_cluster

  shard02:
    image: mongo:latest
    container_name: shard02
    command: mongod --shardsvr --replSet shard2ReplSet --port 27017 --bind_ip_all
    ports:
      - "27020:27017"
    volumes:
      - shard02_data:/data/db
    networks:
      - mongo_cluster

  mongos:
    image: mongo:latest
    container_name: mongos
    depends_on:
      - configsvr
      - shard01
      - shard02
    command: mongos --configdb configReplSet/configsvr:27017 --bind_ip_all --port 27017
    ports:
      - "27017:27017"
    networks:
      - mongo_cluster

volumes:
  config_data:
  shard01_data:
  shard02_data:

networks:
  mongo_cluster:
    driver: bridge

------------------------------------------------------------


============================================================
4. START DOCKER CONTAINERS
============================================================

WHERE:
Windows Command Prompt

Run:

docker compose up -d

Verify:

docker ps

You should see:

configsvr
shard01
shard02
mongos


============================================================
5. CONFIGURE CONFIG SERVER
============================================================

WHERE:
Windows Command Prompt

Run:

docker exec -it configsvr mongosh

Now you are inside Config Server mongosh.

Run:

rs.initiate({
    _id: "configReplSet",
    configsvr: true,
    members: [
        {
            _id: 0,
            host: "configsvr:27017"
        }
    ]
})

Verify:

rs.status()

Look for:

stateStr: "PRIMARY"

Then exit:

exit


============================================================
6. CONFIGURE SHARD 1
============================================================

WHERE:
Windows Command Prompt

Run:

docker exec -it shard01 mongosh

Now you are inside Shard 1 mongosh.

Run:

rs.initiate({
    _id: "shard1ReplSet",
    members: [
        {
            _id: 0,
            host: "shard01:27017"
        }
    ]
})

Verify:

rs.status()

Look for:

stateStr: "PRIMARY"

Exit:

exit


============================================================
7. CONFIGURE SHARD 2
============================================================

WHERE:
Windows Command Prompt

Run:

docker exec -it shard02 mongosh

Now you are inside Shard 2 mongosh.

Run:

rs.initiate({
    _id: "shard2ReplSet",
    members: [
        {
            _id: 0,
            host: "shard02:27017"
        }
    ]
})

Verify:

rs.status()

Look for:

stateStr: "PRIMARY"

Exit:

exit


============================================================
8. CONNECT TO MONGOS
============================================================

WHERE:
Windows Command Prompt

Run:

docker exec -it mongos mongosh

You should see:

[direct: mongos] test>

All remaining MongoDB sharding commands should be
executed through mongos.


============================================================
9. VERIFY MONGOS
============================================================

WHERE:
Inside mongos

Run:

db.hello()

Look for:

msg: "isdbgrid"

This confirms that the connection is through the
MongoDB query router.


============================================================
10. ADD SHARD 1
============================================================

WHERE:
Inside mongos

Run:

sh.addShard("shard1ReplSet/shard01:27017")


============================================================
11. ADD SHARD 2
============================================================

WHERE:
Inside mongos

Run:

sh.addShard("shard2ReplSet/shard02:27017")


============================================================
12. VERIFY THE SHARDED CLUSTER
============================================================

WHERE:
Inside mongos

Run:

sh.status()

You should see:

shard1ReplSet/shard01:27017

and:

shard2ReplSet/shard02:27017


============================================================
13. ENABLE SHARDING FOR COLLEGE DATABASE
============================================================

WHERE:
Inside mongos

Run:

sh.enableSharding("College")

Verify:

sh.status()

Look for:

College


============================================================
14. SWITCH TO COLLEGE DATABASE
============================================================

WHERE:
Inside mongos

Run:

use College

Prompt should become:

[direct: mongos] College>


============================================================
15. CREATE STUDENT COLLECTION
============================================================

WHERE:
Inside mongos

Run:

db.createCollection("Student")

Verify:

show collections

Expected:

Student


============================================================
16. INSERT STUDENT RECORDS
============================================================

WHERE:
Inside mongos

Run:

db.Student.insertMany([
    { RollNo: 1, Name: "Arun", Department: "CSE", Age: 20 },
    { RollNo: 2, Name: "Bala", Department: "CSE", Age: 21 },
    { RollNo: 3, Name: "Charan", Department: "IT", Age: 20 },
    { RollNo: 4, Name: "Dinesh", Department: "CSE", Age: 21 },
    { RollNo: 5, Name: "Elango", Department: "ECE", Age: 22 },
    { RollNo: 6, Name: "Farhan", Department: "IT", Age: 22 },
    { RollNo: 7, Name: "Gokul", Department: "CSE", Age: 20 },
    { RollNo: 8, Name: "Hari", Department: "ECE", Age: 21 },
    { RollNo: 9, Name: "Ishan", Department: "IT", Age: 20 },
    { RollNo: 10, Name: "Karthik", Department: "CSE", Age: 22 }
])

Verify:

db.Student.find().sort({ RollNo: 1 })


============================================================
17. SELECT SHARD KEY
============================================================

For this assignment, use:

RollNo

as the shard key.

The shard key determines how MongoDB divides the
collection into chunks.


============================================================
18. SHARD THE STUDENT COLLECTION
============================================================

WHERE:
Inside mongos

Run:

sh.shardCollection(
    "College.Student",
    { RollNo: 1 }
)

Verify:

sh.status()

Look for:

College.Student

and:

shardKey: { RollNo: 1 }


============================================================
19. CHECK INITIAL DISTRIBUTION
============================================================

WHERE:
Inside mongos

Run:

db.Student.getShardDistribution()

Initially, MongoDB may show all documents on one shard.

This is normal because the collection may initially
contain only one chunk.


============================================================
20. SPLIT THE CHUNK
============================================================

WHERE:
Inside mongos

Run:

sh.splitAt(
    "College.Student",
    { RollNo: 6 }
)

This creates two logical chunks:

Chunk 1:
MinKey -> 6

Chunk 2:
6 -> MaxKey

Verify:

sh.status()


============================================================
21. MOVE LOWER CHUNK TO SHARD 1
============================================================

WHERE:
Inside mongos

Run:

sh.moveChunk(
    "College.Student",
    { RollNo: 1 },
    "shard1ReplSet"
)

This moves the chunk containing RollNo = 1
to shard1ReplSet.

The resulting chunk ownership should be:

MinKey -> 6
    shard1ReplSet

6 -> MaxKey
    shard2ReplSet


============================================================
22. VERIFY FINAL SHARD DISTRIBUTION
============================================================

WHERE:
Inside mongos

Run:

db.Student.getShardDistribution()

Then:

sh.status()

The final output should show data distributed
between both shards.

Expected logical distribution:

Shard 1:
RollNo < 6

Shard 2:
RollNo >= 6


============================================================
23. TEST CASE 1 - SHARD KEY SEARCH
============================================================

WHERE:
Inside mongos

Run:

db.Student.find({ RollNo: 3 })

This searches using the shard key.


============================================================
24. TEST CASE 2 - NON-SHARD-KEY SEARCH
============================================================

WHERE:
Inside mongos

Run:

db.Student.find({ Department: "CSE" })

This searches using a field that is not the shard key.


============================================================
25. TEST CASE 3 - RANGE QUERY
============================================================

WHERE:
Inside mongos

Run:

db.Student.find({
    RollNo: { $gte: 6 }
}).sort({ RollNo: 1 })


============================================================
26. TEST CASE 4 - VERIFY DISTRIBUTION
============================================================

WHERE:
Inside mongos

Run:

db.Student.getShardDistribution()


============================================================
27. FINAL VERIFICATION
============================================================

Run all of the following inside mongos:

db.hello()

sh.status()

db.Student.find().sort({ RollNo: 1 })

db.Student.getShardDistribution()


============================================================
28. IMPORTANT COMMAND LOCATION SUMMARY
============================================================

WINDOWS COMMAND PROMPT:

docker compose up -d

docker ps

docker exec -it configsvr mongosh

docker exec -it shard01 mongosh

docker exec -it shard02 mongosh

docker exec -it mongos mongosh


CONFIG SERVER MONGOSH:

rs.initiate(...)
rs.status()


SHARD 1 MONGOSH:

rs.initiate(...)
rs.status()


SHARD 2 MONGOSH:

rs.initiate(...)
rs.status()


MONGOS:

db.hello()

sh.addShard(...)

sh.status()

sh.enableSharding("College")

use College

db.createCollection("Student")

db.Student.insertMany(...)

sh.shardCollection(...)

db.Student.getShardDistribution()

sh.splitAt(...)

sh.moveChunk(...)

db.Student.find(...)


============================================================
29. RECOMMENDED SCREENSHOTS FOR REPORT
============================================================

1. docker ps
   Shows configsvr, shard01, shard02 and mongos.

2. Config Server rs.status()
   Shows PRIMARY.

3. Shard 1 rs.status()
   Shows PRIMARY.

4. Shard 2 rs.status()
   Shows PRIMARY.

5. mongos db.hello()
   Shows msg: "isdbgrid".

6. sh.status()
   Shows both shards.

7. show collections
   Shows Student.

8. sh.shardCollection(...)
   Shows collectionsharded.

9. sh.splitAt(...)
   Shows successful split.

10. sh.moveChunk(...)
    Shows successful migration.

11. db.Student.getShardDistribution()
    Shows final distribution across shards.

12. Student search queries
    Shows test case outputs.


============================================================
30. FINAL EXPECTED ARCHITECTURE
============================================================

                    MONGODB SHARDED CLUSTER

                         +---------+
                         |  mongos |
                         +----+----+
                              |
                  +-----------+-----------+
                  |                       |
             +----v----+             +----v----+
             | Shard 1 |             | Shard 2 |
             | shard01 |             | shard02 |
             +----+----+             +----+----+
                  |                       |
                  +-----------+-----------+
                              |
                         Student Data

                       Shard Key: RollNo

                  +-----------+-----------+
                  |                       |
              RollNo < 6             RollNo >= 6
                  |                       |
                  v                       v
              Shard 1                 Shard 2


============================================================
END OF IMPLEMENTATION GUIDE
============================================================


---

## 🎯 Viva & Demo Question Bank (Q&A)

### Q1: What is Horizontal Sharding vs Vertical Scaling?
* **Answer:** Vertical scaling (scaling up) increases CPU/RAM on a single machine, which has hardware limits. Horizontal sharding (scaling out) partitions data across multiple independent database servers (shards), enabling linear scalability.

### Q2: What is the role of mongos?
* **Answer:** mongos is an interface/router between the client application and the sharded cluster. It receives queries, checks chunk metadata from the Config Server, routes queries to the appropriate shard(s), and returns merged results to the client.

### Q3: Why is a Config Server necessary?
* **Answer:** The Config Server maintains the metadata for the entire cluster (e.g., which shards exist, what collections are sharded, chunk ranges, and which shard owns which chunk). Without configsvr, mongos cannot route queries correctly.

### Q4: What is a Shard Key and how was it chosen here?
* **Answer:** A Shard Key is a field (or compound fields) in documents that determines how data is distributed across chunks and shards. In this lab, RollNo was chosen as the range-based shard key.

### Q5: Why did we need db.Student.createIndex({ RollNo: 1 }) before sh.shardCollection?
* **Answer:** In MongoDB 8.x+, MongoDB requires an explicit index that starts with the proposed shard key before sh.shardCollection() can be executed.

### Q6: What is the difference between a Targeted Query and a Scatter-Gather Query?
* **Answer:** 
  * **Targeted Query:** Filters by the Shard Key (RollNo). mongos routes the request to only the specific shard containing that data.
  * **Scatter-Gather Query:** Filters by a non-shard key (Department). mongos must broadcast the query to **all shards** in the cluster, wait for responses, and aggregate them.

### Q7: What is a Chunk and why did we run sh.splitAt and sh.moveChunk?
* **Answer:** A chunk is a subset of sharded data bounded by minimum and maximum shard key ranges. We manually split the chunk at RollNo: 6 and migrated the lower chunk (RollNo < 6) to shard01 to demonstrate manual chunk balancing and data distribution across two distinct shards.

---

## 🖼️ Documented Screenshots (docs/ folder)

* docker_ps.png — Verification of all 4 running containers
* configsvr_rs_status_1.png & configsvr_rs_status_2.png — Config server replica set status (PRIMARY)
* shard01_rs_status_1.png & shard01_rs_status_2.png — Shard 1 replica set status (PRIMARY)
* shard02_rs_status_1.png & shard02_rs_status_2.png — Shard 2 replica set status (PRIMARY)
* mongos_db_hello.png — Router connection verification (isdbgrid)
* mongos_sh_status_1.png to mongos_sh_status_7.png — Cluster topology & sharding status
* show_collections.png — Collection list in College database
* query_test_1_shard_key.png — Test Case 1: Targeted query execution
* query_test_2_non_shard_key.png — Test Case 2: Scatter-gather query execution
* query_test_3_range_query_1.png & query_test_3_range_query_2.png — Test Case 3: Range query execution
* get_shard_distribution.png — Test Case 4: Final shard distribution statistics
