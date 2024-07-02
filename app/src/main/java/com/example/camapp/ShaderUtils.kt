package com.example.camapp.utils

import android.opengl.GLES20
import android.util.Log

object ShaderUtils {

    const val VERTEX_SHADER = """
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;
        uniform mat4 u_ProjectionMatrix;
        void main() {
            v_TexCoord = a_TexCoord;
            gl_Position = u_ProjectionMatrix * a_Position;
        }
    """

    const val FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D u_Texture;
        uniform mat4 u_ColorMatrix;
        varying vec2 v_TexCoord;
        void main() {
            vec4 color = texture2D(u_Texture, v_TexCoord);
            gl_FragColor = u_ColorMatrix * color;
        }
    """

    fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                Log.e("ShaderUtils", "Error compiling shader: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Error creating shader.")
            }
        }
    }

    fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e("ShaderUtils", "Error linking program: ${GLES20.glGetProgramInfoLog(program)}")
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Error creating program.")
            }
        }
    }
}
