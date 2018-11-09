module.exports = function (grunt) {
  grunt.initConfig({
    clean: {
      dist: ["dist/"]
    },
    jshint: {
      dist: ['Gruntfile.js', 'js/*.js']
    },
    concat: {
      dist: {
        src: ["js/*.js"],
        dest: "dist/js/scripts.js"
      }
    },
    uglify: {
      dist: {
        src: ["dist/js/scripts.js"],
        dest: "dist/js/scripts.min.js"
      }
    },
    copy: {
      dist: {
        src: ["index.html", "lib/*", "partials/*", "css/*"],
        dest: "dist/"
      }
    },
    karma: {
      dist: {
        configFile: "karma.conf.js"
      }
    },
    connect: {
      dist: {
        options: {
          port: 9001,
          base: 'dist/'
        }
      }
    }
  });
  
  grunt.loadNpmTasks("grunt-contrib-clean");
  grunt.loadNpmTasks("grunt-contrib-jshint");
  grunt.loadNpmTasks("grunt-contrib-concat");
  grunt.loadNpmTasks("grunt-contrib-uglify");
  grunt.loadNpmTasks("grunt-contrib-copy");
  grunt.loadNpmTasks("grunt-karma");
  grunt.loadNpmTasks("grunt-contrib-connect");

  grunt.registerTask("dist", ["clean", "jshint", "concat", "uglify", "copy", "connect:dist:keepalive"]);
}
