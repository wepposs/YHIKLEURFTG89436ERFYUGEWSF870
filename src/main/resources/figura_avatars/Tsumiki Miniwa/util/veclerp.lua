local lerp = math.lerp

function veclerp(vector1, vector2, percent)
    return vec(
        lerp(vector1.x, vector2.x, percent),
            lerp(vector1.y, vector2.y, percent),
            lerp(vector1.z, vector2.z, percent)
    )
end
