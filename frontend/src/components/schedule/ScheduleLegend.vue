<template>
  <div class="schedule-legend">
    <div class="legend-item">
      <span class="legend-color is-available"></span>
      <span class="legend-label">可约时段</span>
    </div>
    <div class="legend-item">
      <span class="legend-color is-booked"></span>
      <span class="legend-label">已约订单</span>
    </div>
    <div class="legend-item">
      <span class="legend-color is-unavailable"></span>
      <span class="legend-label">休息 / 不可约</span>
    </div>
    <div class="legend-item">
      <span class="legend-color is-expired"></span>
      <span class="legend-label">已过期</span>
    </div>
  </div>
</template>

<style scoped>
.schedule-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-color {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  box-sizing: border-box;
}

.legend-color.is-available {
  background-color: #2ca66f;
}

.legend-color.is-booked {
  background-color: #7659c9;
}

.legend-color.is-unavailable {
  background-color: #5f6b7a;
  border: 1px solid #4b5563;
}

.legend-color.is-expired {
  background-color: #6b7280;
  background-image: repeating-linear-gradient(
    -45deg,
    rgba(255, 255, 255, 0.4),
    rgba(255, 255, 255, 0.4) 3px,
    transparent 3px,
    transparent 6px
  );
  opacity: 0.55;
}
</style>
