FROM public.ecr.aws/bitnami/elasticsearch:9.1.2-debian-12-r0

# Install analysis-nori plugin for Korean text analysis
USER 0
RUN /opt/bitnami/elasticsearch/bin/elasticsearch-plugin install --batch analysis-nori

# Use the default non-root user provided by the Bitnami image
USER 1001

