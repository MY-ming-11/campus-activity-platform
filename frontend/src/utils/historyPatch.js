/**
 * 修「窗口点了最小化没反应」的问题。
 *
 * 【成因】
 * vue-router 的 createWebHistory() 会注册 visibilitychange / pagehide 监听，
 * 页面变成 hidden 时它会调 history.replaceState，把当前滚动位置存进 history.state，
 * 目的是刷新页面后能恢复滚动位置。
 *
 * 但「页面变成 hidden」不只发生在关闭页面时，窗口最小化同样是 hidden。
 * 于是最小化的瞬间被塞了一次同步的 history 写入，这个写入会打断 Windows 的最小化，
 * 表现就是点最小化没反应。
 *
 * 【为什么不用「把 visibilitychange 监听全屏蔽掉」那种修法】
 * 那样确实也能好，但它是无差别的：埋点上报、视频播放暂停、编辑内容自动保存
 * 这些正经用途会被一起干掉。而真正惹事的只有一个动作，就是页面隐藏期间写 history。
 *
 * 所以这里只拦这一件事：页面隐藏时把 replaceState 暂存起来，等页面重新可见再补上。
 * 其他 visibilitychange 监听不受影响，vue-router 想存滚动位置这件事也仍然成立。
 *
 * 必须在 createWebHistory() 执行之前引入，见 router/index.js 顶部的 import。
 */

const nativeReplaceState = window.history.replaceState.bind(window.history)

let deferred = null

window.history.replaceState = function replaceState(state, title, url) {
  if (document.visibilityState === 'hidden') {
    deferred = [state, title, url]
    return
  }
  nativeReplaceState(state, title, url)
}

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible' || !deferred) {
    return
  }
  const args = deferred
  deferred = null
  nativeReplaceState(...args)
})

export default nativeReplaceState
