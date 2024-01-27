# build docker images
scala-cli package --docker --docker-image-repository load-balancer LoadBalancer.scala  
scala-cli package --docker --docker-image-repository server Server.scala  

# run services defined in docker compose
docker compose up -d

# send request
curl localhost 

# run test suite
scala-cli test HttpSuite.test.scala