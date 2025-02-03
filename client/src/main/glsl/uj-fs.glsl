#version 300 es
precision highp float;

in vec2 texCoord;
out vec4 fragmentColor;

uniform struct {
	sampler2D currentFrame;
	sampler2D previousFrame;
	float weight;
} mesh;


void main(void) {
	//fragmentColor =texture(mesh.currentFrame, texCoord);

	vec2 correctedTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    vec4 current = texture(mesh.currentFrame, correctedTexCoord);
    vec4 previous = texture(mesh.previousFrame, correctedTexCoord);
    fragmentColor = mix(current, previous, 0.5f);
}
