{
  description = "Development environment for chat-ws-android";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
        config.android_sdk.accept_license = true;
      };

      java = pkgs.jdk17;

      androidPackages = pkgs.androidenv.composeAndroidPackages {
        toolsVersion = "26.1.1";
        platformToolsVersion = "35.0.1";
        buildToolsVersions = ["34.0.0"];
        platformVersions = ["34"];
        includeEmulator = false;
        includeNDK = false;
        useGoogleAPIs = false;
        useGoogleTVAddOns = false;
      };

      androidSdk = androidPackages.androidsdk;
      androidHome = "${androidSdk}/libexec/android-sdk";
      javaHome = "${java}/lib/openjdk";
    in {
      devShells.default = pkgs.mkShell {
        packages = [
          java
          pkgs.gradle_8
          pkgs.kotlin
          pkgs.gnupg
          pkgs.git
          androidSdk
          pkgs.cacert
        ];

        ANDROID_HOME = androidHome;
        ANDROID_SDK_ROOT = androidHome;
        JAVA_HOME = javaHome;
        GRADLE_USER_HOME = ".gradle";

        shellHook = ''
          export PATH=${androidHome}/cmdline-tools/latest/bin:${androidHome}/platform-tools:$PATH
          echo "Dev shell ready. ANDROID_HOME=$ANDROID_HOME JAVA_HOME=$JAVA_HOME"
        '';
      };
    });
}
