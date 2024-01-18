scala-cli package --docker --docker-image-repository load-balancer LoadBalancer.scala  
scala-cli package --docker --docker-image-repository server Server.scala  
docker network create load-balancer
docker run -d -p 80:80 --network=load-balancer --name=load-balancer load-balancer http://172.20.0.1:8081 http://172.20.0.1:8082 http://172.20.0.1:8083
docker run -d -p 8081:8081 --network=load-balancer --name=server-1 server 8081
docker run -d -p 8082:8082 --network=load-balancer --name=server-2 server 8082
docker run -d -p 8083:8083 --network=load-balancer --name=server-3 server 8083