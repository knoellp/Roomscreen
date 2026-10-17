FROM eclipse-temurin:17-jdk-jammy

ARG CMDLINE_TOOLS=14742923
ARG MAESTRO_VERSION=2.11.0
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV MAESTRO_HOME=/opt/maestro
ENV PATH=${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/maestro/bin

RUN apt-get update && apt-get install -y --no-install-recommends \
      curl unzip ca-certificates python3 netcat-openbsd git \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip \
    && unzip -q /tmp/clt.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/clt.zip

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

RUN curl -fsSL -o /tmp/maestro.zip https://github.com/mobile-dev-inc/maestro/releases/download/cli-${MAESTRO_VERSION}/maestro.zip \
    && unzip -q /tmp/maestro.zip -d /opt \
    && rm /tmp/maestro.zip

WORKDIR /work

# Container runs as the host user (see scripts/dock). Provide writable homes
# for arbitrary uids so no root-owned files appear in the project or caches.
ENV HOME=/home/dev
ENV GRADLE_USER_HOME=/gradle-home
RUN mkdir -p /home/dev/.android /gradle-home && chmod -R 777 /home/dev /gradle-home
