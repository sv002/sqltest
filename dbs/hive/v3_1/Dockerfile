FROM centos

# Basic hygene
RUN yum upgrade -y && \
    yum update -y && \
    yum install -y java-1.8.0-openjdk-devel wget sudo unzip git maven which

# Get Hadoop
RUN mkdir /grid && \
    cd /grid &&  \
    wget http://mirrors.ocf.berkeley.edu/apache/hadoop/common/hadoop-3.1.1/hadoop-3.1.1.tar.gz &&  \
    tar zxf hadoop-3.1.1.tar.gz

# Get Hive
RUN cd /grid &&  \
    wget http://mirrors.ocf.berkeley.edu/apache/hive/hive-3.1.1/apache-hive-3.1.1-bin.tar.gz &&  \
    tar zxf apache-hive-3.1.1-bin.tar.gz

RUN mkdir /root/hdfs-scratch && \
    mkdir -p /user/hive/warehouse && \
    mkdir /tmp/hive && \
    chmod 777 /tmp/hive


# Create Hive User
RUN groupadd hive && \
    useradd -g hive --shell=/bin/bash -m -d /home/hive hive && \
    chown -R hive /user/hive/ && \
    chgrp -R hive /user/hive/

USER hive

# Set up environment variables
ENV HIVE_HOME /grid/apache-hive-3.1.1-bin
ENV HADOOP_HOME /grid/hadoop-3.1.1
ENV PATH $PATH:$HADOOP_HOME/bin:$HIVE_HOME/bin
ENV HADOOP_OPTS=-Xmx3072m
ENV JAVA_HOME /usr


EXPOSE 10000
WORKDIR /home/hive

RUN $HIVE_HOME/bin/schematool -dbType derby -initSchema

CMD ["hive", "--service", "hiveserver2", \
      "--hiveconf", "hive.users.in.admin.role=hive", \
      "--hiveconf", "hive.mapred.mode=nonstrict", \
      "--hiveconf", "hive.support.concurrency=true", \
      "--hiveconf", "hive.txn.manager=org.apache.hadoop.hive.ql.lockmgr.DbTxnManager", \
      "--hiveconf", "hive.default.fileformat=ORC" \
    ]
