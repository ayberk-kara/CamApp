package com.example.camapp.renderer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.camapp.utils.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class NoirFilterRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var programHandle: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0
    private var textureUniformHandle: Int = 0
    private var colorMatrixHandle: Int = 0
    private var projectionMatrixHandle: Int = 0
    private var projectionMatrix = FloatArray(16)

    private val vertexData = floatArrayOf(
        -1.0f, -1.0f, 0.0f, 1.0f,
        1.0f, -1.0f, 1.0f, 1.0f,
        -1.0f,  1.0f, 0.0f, 0.0f,
        1.0f,  1.0f, 1.0f, 0.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(vertexData)
            position(0)
        }

    private var filterEnabled = false

    fun setFilterEnabled(enabled: Boolean) {
        filterEnabled = enabled
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        val vertexShader = ShaderUtils.loadShader(GLES20.GL_VERTEX_SHADER, ShaderUtils.VERTEX_SHADER)
        val fragmentShader = ShaderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, ShaderUtils.FRAGMENT_SHADER)
        programHandle = ShaderUtils.createProgram(vertexShader, fragmentShader)

        positionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(programHandle, "a_TexCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(programHandle, "u_Texture")
        colorMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_ColorMatrix")
        projectionMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_ProjectionMatrix")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(programHandle)

        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        if (filterEnabled) {
            val colorMatrix = floatArrayOf(
                0.299f, 0.299f, 0.299f, 0f,
                0.587f, 0.587f, 0.587f, 0f,
                0.114f, 0.114f, 0.114f, 0f,
                0f, 0f, 0f, 1f
            )
            GLES20.glUniformMatrix4fv(colorMatrixHandle, 1, false, colorMatrix, 0)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
    }
}
